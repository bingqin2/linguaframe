package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class LocalizationJobRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private ModelCallRepository modelCallRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
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
    void savesAndFindsLocalizationJobRecord() {
        Instant createdAt = Instant.parse("2026-06-25T15:00:00Z");
        videoRepository.save(new VideoRecord(
                "video-job-1",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/video-job-1/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        LocalizationJobRecord record = new LocalizationJobRecord(
                "job-1",
                "video-job-1",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        );

        jobRepository.save(record);

        Optional<LocalizationJobRecord> found = jobRepository.findById("job-1");

        assertThat(found).contains(record);
    }

    @Test
    void returnsEmptyWhenJobDoesNotExist() {
        assertThat(jobRepository.findById("missing-job")).isEmpty();
    }

    @Test
    void claimsQueuedAndRetryingJobsForExecutionOnlyOnce() {
        Instant now = Instant.parse("2026-06-26T09:00:00Z");
        createJob("video-claim-queued", "job-claim-queued", LocalizationJobStatus.QUEUED, now);
        createJob("video-claim-retrying", "job-claim-retrying", LocalizationJobStatus.RETRYING, now);
        createJob("video-claim-completed", "job-claim-completed", LocalizationJobStatus.COMPLETED, now);

        assertThat(jobRepository.claimForExecution("job-claim-queued", now.plusSeconds(1))).isTrue();
        assertThat(jobRepository.claimForExecution("job-claim-retrying", now.plusSeconds(2))).isTrue();
        assertThat(jobRepository.claimForExecution("job-claim-completed", now.plusSeconds(3))).isFalse();
        assertThat(jobRepository.claimForExecution("job-claim-queued", now.plusSeconds(4))).isFalse();

        assertThat(jobRepository.findById("job-claim-queued"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
                    assertThat(job.startedAt()).isEqualTo(now.plusSeconds(1));
                    assertThat(job.updatedAt()).isEqualTo(now.plusSeconds(1));
                });
    }

    @Test
    void marksCompletedAndClearsFailureFields() {
        Instant now = Instant.parse("2026-06-26T10:00:00Z");
        createJob("video-complete", "job-complete", LocalizationJobStatus.QUEUED, now);
        jobRepository.claimForExecution("job-complete", now.plusSeconds(1));
        jobRepository.markFailed("job-complete", LocalizationJobStage.WORKER_SMOKE, "temporary failure", now.plusSeconds(2));

        jobRepository.markCompleted("job-complete", now.plusSeconds(3));

        assertThat(jobRepository.findById("job-complete"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
                    assertThat(job.completedAt()).isEqualTo(now.plusSeconds(3));
                    assertThat(job.failureStage()).isNull();
                    assertThat(job.failureReason()).isNull();
                    assertThat(job.failedAt()).isNull();
                });
    }

    @Test
    void marksFailedWithTruncatedFailureReason() {
        Instant now = Instant.parse("2026-06-26T11:00:00Z");
        createJob("video-fail", "job-fail", LocalizationJobStatus.QUEUED, now);
        jobRepository.claimForExecution("job-fail", now.plusSeconds(1));

        jobRepository.markFailed("job-fail", LocalizationJobStage.WORKER_SMOKE, "x".repeat(700), now.plusSeconds(2));

        assertThat(jobRepository.findById("job-fail"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.FAILED);
                    assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.WORKER_SMOKE);
                    assertThat(job.failureReason()).hasSize(512);
                    assertThat(job.failedAt()).isEqualTo(now.plusSeconds(2));
                });
    }

    @Test
    void marksFailedJobAsRetryingAndIncrementsRetryCount() {
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        createJob("video-retry", "job-retry", LocalizationJobStatus.QUEUED, now);
        jobRepository.claimForExecution("job-retry", now.plusSeconds(1));
        jobRepository.markFailed("job-retry", LocalizationJobStage.WORKER_SMOKE, "failed once", now.plusSeconds(2));

        assertThat(jobRepository.markRetrying("job-retry", now.plusSeconds(3))).isTrue();
        assertThat(jobRepository.markRetrying("job-retry", now.plusSeconds(4))).isFalse();

        assertThat(jobRepository.findById("job-retry"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.RETRYING);
                    assertThat(job.retryCount()).isEqualTo(1);
                    assertThat(job.failureStage()).isNull();
                    assertThat(job.failureReason()).isNull();
                    assertThat(job.failedAt()).isNull();
                    assertThat(job.updatedAt()).isEqualTo(now.plusSeconds(3));
                });
    }

    @Test
    void marksQueuedRetryingAndProcessingJobsCancelled() {
        Instant now = Instant.parse("2026-06-27T03:00:00Z");
        createJob("video-cancel-queued", "job-cancel-queued", LocalizationJobStatus.QUEUED, now);
        createJob("video-cancel-retrying", "job-cancel-retrying", LocalizationJobStatus.RETRYING, now);
        createJob("video-cancel-processing", "job-cancel-processing", LocalizationJobStatus.PROCESSING, now);

        assertThat(jobRepository.markCancelled("job-cancel-queued", now.plusSeconds(1))).isTrue();
        assertThat(jobRepository.markCancelled("job-cancel-retrying", now.plusSeconds(2))).isTrue();
        assertThat(jobRepository.markCancelled("job-cancel-processing", now.plusSeconds(3))).isTrue();

        assertThat(jobRepository.findById("job-cancel-processing"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
                    assertThat(job.completedAt()).isEqualTo(now.plusSeconds(3));
                    assertThat(job.failedAt()).isNull();
                    assertThat(job.failureStage()).isNull();
                    assertThat(job.failureReason()).isNull();
                    assertThat(job.updatedAt()).isEqualTo(now.plusSeconds(3));
                });
    }

    @Test
    void doesNotCancelTerminalJobsAndDetectsCancelledJobs() {
        Instant now = Instant.parse("2026-06-27T03:10:00Z");
        createJob("video-cancel-completed", "job-cancel-completed", LocalizationJobStatus.COMPLETED, now);
        createJob("video-cancel-failed", "job-cancel-failed", LocalizationJobStatus.FAILED, now);
        createJob("video-cancel-cancelled", "job-cancel-cancelled", LocalizationJobStatus.CANCELLED, now);

        assertThat(jobRepository.markCancelled("job-cancel-completed", now.plusSeconds(1))).isFalse();
        assertThat(jobRepository.markCancelled("job-cancel-failed", now.plusSeconds(1))).isFalse();
        assertThat(jobRepository.markCancelled("job-cancel-cancelled", now.plusSeconds(1))).isFalse();
        assertThat(jobRepository.isCancelled("job-cancel-cancelled")).isTrue();
        assertThat(jobRepository.isCancelled("missing-job")).isFalse();
    }

    @Test
    void findsJobSummariesOrderedNewestFirstWithEstimatedCost() {
        Instant base = Instant.parse("2026-06-26T13:00:00Z");
        createJob("video-summary-old", "job-summary-old", LocalizationJobStatus.COMPLETED, base);
        createJob("video-summary-newest", "job-summary-newest", LocalizationJobStatus.PROCESSING, base.plusSeconds(20));
        createJob("video-summary-middle", "job-summary-middle", LocalizationJobStatus.FAILED, base.plusSeconds(10));
        saveModelCall("model-call-newest-1", "job-summary-newest", new BigDecimal("0.00010000"), base.plusSeconds(21));
        saveModelCall("model-call-newest-2", "job-summary-newest", new BigDecimal("0.00020000"), base.plusSeconds(22));
        saveModelCall("model-call-middle-1", "job-summary-middle", new BigDecimal("0.00030000"), base.plusSeconds(11));

        assertThat(jobRepository.findSummaries(null, 2, 0))
                .extracting("jobId")
                .containsExactly("job-summary-newest", "job-summary-middle");
        assertThat(jobRepository.findSummaries(null, 2, 0).getFirst())
                .satisfies(summary -> {
                    assertThat(summary.videoId()).isEqualTo("video-summary-newest");
                    assertThat(summary.filename()).isEqualTo("sample-video-summary-newest.mp4");
                    assertThat(summary.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
                    assertThat(summary.estimatedCostUsd()).isEqualByComparingTo("0.00030000");
                });
    }

    @Test
    void findsJobSummariesFilteredByStatusAndCountsTotals() {
        Instant base = Instant.parse("2026-06-26T14:00:00Z");
        createJob("video-summary-queued", "job-summary-queued", LocalizationJobStatus.QUEUED, base);
        createJob("video-summary-failed", "job-summary-failed", LocalizationJobStatus.FAILED, base.plusSeconds(10));
        createJob("video-summary-completed", "job-summary-completed", LocalizationJobStatus.COMPLETED, base.plusSeconds(20));

        assertThat(jobRepository.findSummaries(LocalizationJobStatus.FAILED, 20, 0))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.jobId()).isEqualTo("job-summary-failed");
                    assertThat(summary.status()).isEqualTo(LocalizationJobStatus.FAILED);
                    assertThat(summary.filename()).isEqualTo("sample-video-summary-failed.mp4");
                });
        assertThat(jobRepository.countSummaries(null)).isEqualTo(3);
        assertThat(jobRepository.countSummaries(LocalizationJobStatus.FAILED)).isEqualTo(1);
    }

    private void createJob(String videoId, String jobId, LocalizationJobStatus status, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample-" + videoId + ".mp4",
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
                status,
                createdAt
        ));
    }

    private void saveModelCall(String id, String jobId, BigDecimal estimatedCostUsd, Instant createdAt) {
        modelCallRepository.save(new ModelCallRecord(
                id,
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                ModelCallStatus.SUCCEEDED,
                125L,
                1000,
                500,
                null,
                null,
                estimatedCostUsd,
                null,
                createdAt
        ));
    }
}
