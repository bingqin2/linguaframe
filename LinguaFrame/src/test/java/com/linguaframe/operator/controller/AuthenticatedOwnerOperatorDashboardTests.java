package com.linguaframe.operator.controller;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "linguaframe.auth.enabled=true",
        "linguaframe.auth.owner-username=owner",
        "linguaframe.auth.owner-password=owner-password",
        "linguaframe.auth.jwt-secret=0123456789abcdef0123456789abcdef",
        "linguaframe.demo.owner-id=owner-alpha",
        "linguaframe.demo.access-token=demo-token"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticatedOwnerOperatorDashboardTests {

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
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void bearerAuthDashboardShowsOnlyCurrentOwnerFailuresAndCounts() throws Exception {
        Instant base = Instant.parse("2026-06-27T04:00:00Z");
        createFailedJob("dashboard-owner-alpha-video", "dashboard-owner-alpha-job", "owner-alpha", "alpha-failed.mp4", base);
        createFailedJob("dashboard-owner-beta-video", "dashboard-owner-beta-job", "owner-beta", "beta-failed.mp4", base.plusSeconds(10));

        mockMvc.perform(get("/api/operator/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + loginToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("owner-alpha"))
                .andExpect(jsonPath("$.ownershipScope").value("LOCAL_AUTH_OWNER"))
                .andExpect(jsonPath("$.statusCounts[?(@.status == 'FAILED')].count").value(1))
                .andExpect(jsonPath("$.recentFailures[0].jobId").value("dashboard-owner-alpha-job"))
                .andExpect(jsonPath("$.recentFailures[0].filename").value("alpha-failed.mp4"))
                .andExpect(content().string(not(containsString("dashboard-owner-beta-job"))))
                .andExpect(content().string(not(containsString("beta-failed.mp4"))));
    }

    private void createFailedJob(String videoId, String jobId, String ownerId, String filename, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                ownerId,
                filename,
                "video/mp4",
                123L,
                null,
                "source-videos/" + videoId + "/" + filename,
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                ownerId,
                "zh-CN",
                null,
                "NATURAL",
                "STANDARD",
                "[]",
                "",
                0,
                "OFF",
                null,
                LocalizationJobStatus.FAILED,
                createdAt
        ));
        jobRepository.markFailed(jobId, LocalizationJobStage.WORKER_SMOKE, "worker failed", createdAt.plusSeconds(1));
        timelineEventRepository.save(new JobTimelineEventRecord(
                jobId + "-timeline",
                jobId,
                LocalizationJobStage.WORKER_SMOKE,
                JobTimelineEventStatus.FAILED,
                "WORKER_SMOKE failed.",
                1000L,
                "worker failed",
                createdAt.plusSeconds(1)
        ));
    }

    private String loginToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owner\",\"password\":\"owner-password\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        int start = body.indexOf("\"token\":\"") + "\"token\":\"".length();
        int end = body.indexOf('"', start);
        return body.substring(start, end);
    }
}
