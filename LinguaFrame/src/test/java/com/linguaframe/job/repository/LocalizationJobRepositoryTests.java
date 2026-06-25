package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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

    private void createJob(String videoId, String jobId, LocalizationJobStatus status, Instant createdAt) {
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
                status,
                createdAt
        ));
    }
}
