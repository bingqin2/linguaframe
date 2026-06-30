package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.JobStateConflictException;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.StuckJobRecoveryVo;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.StuckJobRecoveryServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class StuckJobRecoveryServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-30T10:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository dispatchEventRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

    @Autowired
    private VideoRepository videoRepository;

    private StuckJobRecoveryService service;
    private LocalizationJobCancellationService cancellationService;
    private LocalizationJobRetryService retryService;
    private LocalizationJobStatusCacheService cacheService;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();

        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getWorker().setStageTimeoutSeconds(900);
        properties.getWorker().setMaxRetries(1);
        cancellationService = mock(LocalizationJobCancellationService.class);
        retryService = mock(LocalizationJobRetryService.class);
        cacheService = mock(LocalizationJobStatusCacheService.class);
        service = new StuckJobRecoveryServiceImpl(
                jobRepository,
                videoRepository,
                dispatchEventRepository,
                timelineEventRepository,
                cancellationService,
                retryService,
                cacheService,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                properties,
                FIXED_CLOCK
        );
    }

    @Test
    void classifiesQueuedJobWithOldPendingDispatchAsStaleAndRequeuesWithConfirmation() {
        insertVideo("video-stale");
        insertJob("job-stale", "video-stale", LocalizationJobStatus.QUEUED, 0, Instant.parse("2026-06-30T09:30:00Z"));
        insertDispatch("dispatch-old", "job-stale", JobDispatchEventStatus.PENDING, null, Instant.parse("2026-06-30T09:30:00Z"));

        StuckJobRecoveryVo recovery = service.recovery("job-stale");

        assertThat(recovery.classification()).isEqualTo("QUEUED_STALE_DISPATCH");
        assertThat(recovery.attentionLevel()).isEqualTo("BLOCKED");
        assertThat(recovery.actions()).filteredOn(action -> action.id().equals("REQUEUE_DISPATCH"))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.enabled()).isTrue();
                    assertThat(action.requiresConfirmation()).isTrue();
                });
        assertThat(recovery.markdown()).doesNotContain("/Users/example", "sk-test", "provider payload", "raw transcript text");

        StuckJobRecoveryVo refreshed = service.runAction("job-stale", "REQUEUE_DISPATCH", "REQUEUE_DISPATCH");

        assertThat(refreshed.classification()).isEqualTo("QUEUED_WAITING");
        assertThat(dispatchEventRepository.findReadyToDispatch(Instant.parse("2026-06-30T10:00:01Z"), 10))
                .extracting(JobDispatchEventRecord::jobId)
                .contains("job-stale");
        assertThat(timelineEventRepository.findByJobId("job-stale"))
                .extracting(JobTimelineEventRecord::message)
                .contains("Stale dispatch requeued by operator.");
        verify(cacheService).evict("job-stale");
    }

    @Test
    void classifiesProcessingJobWithOldStartedStageAsStaleWithoutRequeueAction() {
        insertVideo("video-processing");
        insertJob("job-processing", "video-processing", LocalizationJobStatus.PROCESSING, 0, Instant.parse("2026-06-30T09:00:00Z"));
        insertDispatch("dispatch-processing", "job-processing", JobDispatchEventStatus.DISPATCHED, Instant.parse("2026-06-30T09:01:00Z"), Instant.parse("2026-06-30T09:00:00Z"));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "event-processing",
                "job-processing",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.STARTED,
                "raw transcript text /Users/example sk-test provider payload",
                null,
                null,
                Instant.parse("2026-06-30T09:40:00Z")
        ));

        StuckJobRecoveryVo recovery = service.recovery("job-processing");

        assertThat(recovery.classification()).isEqualTo("PROCESSING_STALE_STAGE");
        assertThat(recovery.attentionLevel()).isEqualTo("ATTENTION");
        assertThat(recovery.actions()).filteredOn(action -> action.id().equals("CANCEL_JOB"))
                .singleElement()
                .satisfies(action -> assertThat(action.enabled()).isTrue());
        assertThat(recovery.actions()).filteredOn(action -> action.id().equals("REQUEUE_DISPATCH"))
                .singleElement()
                .satisfies(action -> assertThat(action.enabled()).isFalse());
        assertThat(recovery.markdown()).doesNotContain("/Users/example", "sk-test", "provider payload", "raw transcript text");
    }

    @Test
    void classifiesFailedJobAsRetryableUntilRetryLimit() {
        insertVideo("video-failed");
        insertJob("job-failed", "video-failed", LocalizationJobStatus.FAILED, 0, Instant.parse("2026-06-30T09:45:00Z"));

        StuckJobRecoveryVo recovery = service.recovery("job-failed");

        assertThat(recovery.classification()).isEqualTo("FAILED_RETRYABLE");
        assertThat(recovery.actions()).filteredOn(action -> action.id().equals("RETRY_FAILED_JOB"))
                .singleElement()
                .satisfies(action -> assertThat(action.enabled()).isTrue());

        insertVideo("video-blocked");
        insertJob("job-blocked", "video-blocked", LocalizationJobStatus.FAILED, 1, Instant.parse("2026-06-30T09:45:00Z"));

        StuckJobRecoveryVo blocked = service.recovery("job-blocked");

        assertThat(blocked.classification()).isEqualTo("FAILED_BLOCKED");
        assertThat(blocked.actions()).filteredOn(action -> action.id().equals("RETRY_FAILED_JOB"))
                .singleElement()
                .satisfies(action -> assertThat(action.enabled()).isFalse());
    }

    @Test
    void delegatesCancelAndRetryActionsToExistingServices() {
        insertVideo("video-cancel");
        insertJob("job-cancel", "video-cancel", LocalizationJobStatus.PROCESSING, 0, Instant.parse("2026-06-30T09:00:00Z"));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "event-cancel",
                "job-cancel",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.STARTED,
                "stage running too long",
                null,
                null,
                Instant.parse("2026-06-30T09:40:00Z")
        ));

        service.runAction("job-cancel", "CANCEL_JOB", "CANCEL_JOB");

        verify(cancellationService).cancelJob("job-cancel");

        insertVideo("video-retry-action");
        insertJob("job-retry-action", "video-retry-action", LocalizationJobStatus.FAILED, 0, Instant.parse("2026-06-30T09:45:00Z"));

        service.runAction("job-retry-action", "RETRY_FAILED_JOB", "RETRY_FAILED_JOB");

        verify(retryService).retryFailedJob("job-retry-action");
    }

    @Test
    void rejectsActionWhenConfirmationDoesNotMatch() {
        insertVideo("video-confirm");
        insertJob("job-confirm", "video-confirm", LocalizationJobStatus.QUEUED, 0, Instant.parse("2026-06-30T09:30:00Z"));
        insertDispatch("dispatch-confirm", "job-confirm", JobDispatchEventStatus.PENDING, null, Instant.parse("2026-06-30T09:30:00Z"));

        assertThatThrownBy(() -> service.runAction("job-confirm", "REQUEUE_DISPATCH", "wrong"))
                .isInstanceOf(JobStateConflictException.class)
                .hasMessageContaining("confirmation");
    }

    private void insertVideo(String id) {
        videoRepository.save(new VideoRecord(
                id,
                "owner-demo",
                id + ".mp4",
                "video/mp4",
                1024L,
                null,
                "sha-" + id,
                "uploads/" + id + "/source.mp4",
                MediaUploadStatus.UPLOADED,
                Instant.parse("2026-06-30T09:00:00Z")
        ));
    }

    private void insertJob(String id, String videoId, LocalizationJobStatus status, int retryCount, Instant createdAt) {
        jobRepository.save(new LocalizationJobRecord(
                id,
                videoId,
                "owner-demo",
                "zh-CN",
                "verse",
                "NATURAL",
                "STANDARD",
                "[]",
                "",
                0,
                "OFF",
                "tears-showcase",
                status,
                createdAt,
                status == LocalizationJobStatus.PROCESSING ? createdAt.plusSeconds(60) : null,
                null,
                status == LocalizationJobStatus.FAILED ? createdAt.plusSeconds(120) : null,
                status == LocalizationJobStatus.FAILED ? LocalizationJobStage.TARGET_SUBTITLE_EXPORT : null,
                status == LocalizationJobStatus.FAILED ? "safe failure summary" : null,
                retryCount,
                createdAt
        ));
    }

    private void insertDispatch(String id, String jobId, JobDispatchEventStatus status, Instant dispatchedAt, Instant createdAt) {
        dispatchEventRepository.save(new JobDispatchEventRecord(
                id,
                jobId,
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                payload(jobId),
                status,
                status == JobDispatchEventStatus.DISPATCHED ? 1 : 0,
                createdAt,
                null,
                dispatchedAt,
                createdAt,
                createdAt
        ));
    }

    private String payload(String jobId) {
        try {
            return new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(new QueuedLocalizationJobMessage(
                    jobId,
                    "video-" + jobId,
                    "uploads/source.mp4",
                    "zh-CN",
                    "verse",
                    "NATURAL",
                    "STANDARD",
                    null,
                    null,
                    0,
                    "OFF",
                    "tears-showcase",
                    Instant.parse("2026-06-30T09:00:00Z"),
                    LocalizationJobStage.WORKER_SMOKE
            ));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
