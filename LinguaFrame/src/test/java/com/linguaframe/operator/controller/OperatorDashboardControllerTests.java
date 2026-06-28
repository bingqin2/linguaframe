package com.linguaframe.operator.controller;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperatorDashboardControllerTests {

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class OpenDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private VideoRepository videoRepository;

        @Autowired
        private LocalizationJobRepository jobRepository;

        @Autowired
        private JobTimelineEventRepository timelineEventRepository;

        @Autowired
        private JdbcClient jdbcClient;

        @BeforeEach
        void cleanDatabase() {
            jdbcClient.sql("DELETE FROM quality_evaluations").update();
            jdbcClient.sql("DELETE FROM model_call_records").update();
            jdbcClient.sql("DELETE FROM subtitle_segments").update();
            jdbcClient.sql("DELETE FROM transcript_segments").update();
            jdbcClient.sql("DELETE FROM job_artifacts").update();
            jdbcClient.sql("DELETE FROM job_timeline_events").update();
            jdbcClient.sql("DELETE FROM job_dispatch_events").update();
            jdbcClient.sql("DELETE FROM localization_jobs").update();
            jdbcClient.sql("DELETE FROM videos").update();
        }

        @Test
        void returnsOperatorDashboard() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("dashboard-controller-video", "dashboard-controller-job", "failed.mp4",
                    LocalizationJobStatus.FAILED, createdAt);
            jobRepository.markFailed(
                    "dashboard-controller-job",
                    LocalizationJobStage.WORKER_SMOKE,
                    "worker smoke failed",
                    createdAt.plusSeconds(1)
            );
            timelineEventRepository.save(new JobTimelineEventRecord(
                    "dashboard-controller-stage-timing",
                    "dashboard-controller-job",
                    LocalizationJobStage.WORKER_SMOKE,
                    JobTimelineEventStatus.FAILED,
                    "WORKER_SMOKE failed.",
                    1500L,
                    "worker smoke failed",
                    createdAt.plusSeconds(1)
            ));

            mockMvc.perform(get("/api/operator/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.statusCounts").isArray())
                    .andExpect(jsonPath("$.statusCounts[?(@.status == 'FAILED')].count").value(1))
                    .andExpect(jsonPath("$.recentFailures[0].jobId").value("dashboard-controller-job"))
                    .andExpect(jsonPath("$.recentFailures[0].filename").value("failed.mp4"))
                    .andExpect(jsonPath("$.recentFailures[0].failureStage").value("WORKER_SMOKE"))
                    .andExpect(jsonPath("$.modelCalls.modelCallCount").value(0))
                    .andExpect(jsonPath("$.modelCalls.failedModelCallCount").value(0))
                    .andExpect(jsonPath("$.modelCalls.estimatedCostUsd").value(0))
                    .andExpect(jsonPath("$.cache.artifactCacheHitCount").value(0))
                    .andExpect(jsonPath("$.cache.providerCacheHitCount").value(0))
                    .andExpect(jsonPath("$.stageTimings[0].stage").value("WORKER_SMOKE"))
                    .andExpect(jsonPath("$.stageTimings[0].failedEventCount").value(1))
                    .andExpect(jsonPath("$.stageTimings[0].maxDurationMs").value(1500));
        }

        @Test
        void returnsPrivateDemoOperationsReadiness() throws Exception {
            Instant createdAt = Instant.parse("2026-06-27T07:00:00Z");
            createJob("operations-controller-video", "operations-controller-job", "operations.mp4",
                    LocalizationJobStatus.COMPLETED, createdAt);

            mockMvc.perform(get("/api/operator/private-demo/operations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.overallStatus").exists())
                    .andExpect(jsonPath("$.readyCount").isNumber())
                    .andExpect(jsonPath("$.attentionCount").isNumber())
                    .andExpect(jsonPath("$.blockedCount").isNumber())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Access gate')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Live dependencies')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Cost safety')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Storage and recovery')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Retention cleanup')]").exists())
                    .andExpect(jsonPath("$.sections[?(@.title == 'Demo evidence')]").exists())
                    .andExpect(jsonPath("$.commands[?(@.command == 'scripts/demo/private-demo-preflight.sh')]").exists())
                    .andExpect(jsonPath("$.commands[?(@.command == 'scripts/demo/private-demo-backup.sh --dry-run')]").exists())
                    .andExpect(jsonPath("$.documentationLinks[?(@.path == 'docs/deployment/private-demo.md')]").exists());
        }

        private void createJob(
                String videoId,
                String jobId,
                String filename,
                LocalizationJobStatus status,
                Instant createdAt
        ) {
            videoRepository.save(new VideoRecord(
                    videoId,
                    filename,
                    "video/mp4",
                    123L,
                    "source-videos/" + videoId + "/" + filename,
                    MediaUploadStatus.UPLOADED,
                    createdAt
            ));
            jobRepository.save(new LocalizationJobRecord(jobId, videoId, "zh-CN", status, createdAt));
        }
    }

    @Nested
    @SpringBootTest(properties = "linguaframe.demo.access-token=test-token")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class GatedDemoMode {

        @Autowired
        private MockMvc mockMvc;

        @Test
        void requiresDemoTokenWhenGateIsConfigured() throws Exception {
            mockMvc.perform(get("/api/operator/dashboard"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/dashboard")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/operator/private-demo/operations"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/operator/private-demo/operations")
                            .header("X-LinguaFrame-Demo-Token", "test-token"))
                    .andExpect(status().isOk());
        }
    }
}
