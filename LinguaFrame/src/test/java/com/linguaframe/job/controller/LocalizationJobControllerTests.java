package com.linguaframe.job.controller;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.TranscriptService;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalizationJobControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository dispatchEventRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

    @Autowired
    private TranscriptService transcriptService;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void returnsQueuedLocalizationJobWithDispatchState() throws Exception {
        Instant createdAt = Instant.parse("2026-06-25T15:00:00Z");
        videoRepository.save(new VideoRecord(
                "job-controller-video",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/job-controller-video/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-job",
                "job-controller-video",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
        dispatchEventRepository.save(new JobDispatchEventRecord(
                "job-controller-dispatch-event",
                "job-controller-job",
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{\"jobId\":\"job-controller-job\"}",
                JobDispatchEventStatus.PENDING,
                0,
                createdAt,
                null,
                null,
                createdAt,
                createdAt
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.dispatchStatus").value("PENDING"))
                .andExpect(jsonPath("$.dispatchAttempts").value(0))
                .andExpect(jsonPath("$.dispatchedAt").doesNotExist())
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.timelineEvents").isArray())
                .andExpect(jsonPath("$.timelineEvents").isEmpty());
    }

    @Test
    void returnsQueuedLocalizationJobWithoutDispatchEvent() throws Exception {
        Instant createdAt = Instant.parse("2026-06-25T16:00:00Z");
        videoRepository.save(new VideoRecord(
                "job-controller-video-no-dispatch",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/job-controller-video-no-dispatch/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-job-no-dispatch",
                "job-controller-video-no-dispatch",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job-no-dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-no-dispatch"))
                .andExpect(jsonPath("$.dispatchStatus").doesNotExist())
                .andExpect(jsonPath("$.dispatchAttempts").value(0))
                .andExpect(jsonPath("$.dispatchedAt").doesNotExist())
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.timelineEvents").isArray())
                .andExpect(jsonPath("$.timelineEvents").isEmpty());
    }

    @Test
    void returnsFailedLocalizationJobWithFailureStateAndTimelineEvents() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T19:00:00Z");
        createJob("job-controller-video-failed", "job-controller-job-failed", createdAt);
        jobRepository.claimForExecution("job-controller-job-failed", createdAt.plusSeconds(1));
        jobRepository.markFailed(
                "job-controller-job-failed",
                LocalizationJobStage.WORKER_SMOKE,
                "stage failed safely",
                createdAt.plusSeconds(2)
        );
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-timeline-2",
                "job-controller-job-failed",
                LocalizationJobStage.WORKER_SMOKE,
                JobTimelineEventStatus.FAILED,
                "Smoke stage failed.",
                10L,
                "stage failed safely",
                createdAt.plusSeconds(2)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-timeline-1",
                "job-controller-job-failed",
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.STARTED,
                "Worker received localization job.",
                null,
                null,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureStage").value("WORKER_SMOKE"))
                .andExpect(jsonPath("$.failureReason").value("stage failed safely"))
                .andExpect(jsonPath("$.failedAt").exists())
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.timelineEvents[0].stage").value("WORKER_RECEIVED"))
                .andExpect(jsonPath("$.timelineEvents[0].status").value("STARTED"))
                .andExpect(jsonPath("$.timelineEvents[1].stage").value("WORKER_SMOKE"))
                .andExpect(jsonPath("$.timelineEvents[1].status").value("FAILED"))
                .andExpect(jsonPath("$.timelineEvents[1].errorSummary").value("stage failed safely"));
    }

    @Test
    void retriesFailedLocalizationJobAndCreatesPendingDispatchEvent() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T20:00:00Z");
        createJob("job-controller-video-retry", "job-controller-job-retry", createdAt);
        jobRepository.claimForExecution("job-controller-job-retry", createdAt.plusSeconds(1));
        jobRepository.markFailed(
                "job-controller-job-retry",
                LocalizationJobStage.WORKER_SMOKE,
                "first execution failed",
                createdAt.plusSeconds(2)
        );

        mockMvc.perform(post("/api/jobs/{jobId}/retry", "job-controller-job-retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-retry"))
                .andExpect(jsonPath("$.status").value("RETRYING"))
                .andExpect(jsonPath("$.retryCount").value(1))
                .andExpect(jsonPath("$.failureStage").doesNotExist())
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        dispatchEventRepository.findLatestByJobId("job-controller-job-retry")
                .ifPresentOrElse(
                        event -> {
                            org.assertj.core.api.Assertions.assertThat(event.status()).isEqualTo(JobDispatchEventStatus.PENDING);
                            org.assertj.core.api.Assertions.assertThat(event.payloadJson()).contains("job-controller-job-retry");
                        },
                        () -> org.assertj.core.api.Assertions.fail("Expected retry dispatch event.")
                );
    }

    @Test
    void listsArtifactsForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T22:00:00Z");
        createJob("job-controller-video-artifact", "job-controller-job-artifact", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-artifact-1",
                "job-controller-job-artifact",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-artifact/job-controller-artifact-1/worker-summary.json",
                "worker-summary.json",
                "application/json",
                42L,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}/artifacts", "job-controller-job-artifact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactId").value("job-controller-artifact-1"))
                .andExpect(jsonPath("$[0].jobId").value("job-controller-job-artifact"))
                .andExpect(jsonPath("$[0].type").value("WORKER_SUMMARY"))
                .andExpect(jsonPath("$[0].filename").value("worker-summary.json"))
                .andExpect(jsonPath("$[0].contentType").value("application/json"))
                .andExpect(jsonPath("$[0].sizeBytes").value(42))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void downloadsArtifactForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T23:00:00Z");
        createJob("job-controller-video-download", "job-controller-job-download", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-artifact-download",
                "job-controller-job-download",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-download/job-controller-artifact-download/worker-summary.json",
                "worker-summary.json",
                "application/json",
                11L,
                createdAt.plusSeconds(1)
        ));
        when(objectStorageService.open("job-artifacts/job-controller-job-download/job-controller-artifact-download/worker-summary.json"))
                .thenReturn(new ByteArrayInputStream("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "job-controller-job-download",
                        "job-controller-artifact-download"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"worker-summary.json\""))
                .andExpect(header().longValue("Content-Length", 11L))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string("{\"ok\":true}"));
    }

    @Test
    void returnsTranscriptSegmentsForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T00:00:00Z");
        createJob("job-controller-video-transcript", "job-controller-job-transcript", createdAt);
        transcriptService.replaceTranscript("job-controller-job-transcript", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_200L, "First line"),
                new TranscriptionSegmentBo(1, 1_200L, 2_400L, "Second line")
        )));

        mockMvc.perform(get("/api/jobs/{jobId}/transcript", "job-controller-job-transcript"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].startMs").value(0))
                .andExpect(jsonPath("$[0].endMs").value(1200))
                .andExpect(jsonPath("$[0].text").value("First line"))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].text").value("Second line"));
    }

    @Test
    void returnsNotFoundWhenArtifactDoesNotBelongToJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T23:30:00Z");
        createJob("job-controller-video-artifact-owner", "job-controller-job-artifact-owner", createdAt);
        createJob("job-controller-video-artifact-other", "job-controller-job-artifact-other", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-artifact-owner",
                "job-controller-job-artifact-owner",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-artifact-owner/job-controller-artifact-owner/worker-summary.json",
                "worker-summary.json",
                "application/json",
                2L,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "job-controller-job-artifact-other",
                        "job-controller-artifact-owner"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsRetryForNonFailedLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T21:00:00Z");
        createJob("job-controller-video-not-failed", "job-controller-job-not-failed", createdAt);

        mockMvc.perform(post("/api/jobs/{jobId}/retry", "job-controller-job-not-failed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/jobs/{jobId}", "missing-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void createJob(String videoId, String jobId, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/" + videoId + "/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
    }
}
