package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.RetentionCleanupServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RetentionCleanupServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-27T12:00:00Z");

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

    @Autowired
    private JobDispatchEventRepository dispatchEventRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

    @Autowired
    private JdbcClient jdbcClient;

    private RecordingObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() {
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM quality_evaluations").update();
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
        objectStorageService = new RecordingObjectStorageService();
    }

    @Test
    void dryRunReportsEligibleObjectsAndDeletesNothing() {
        createJobWithArtifact(
                "retention-video-dry",
                "retention-job-dry",
                LocalizationJobStatus.COMPLETED,
                NOW.minusSeconds(10 * 86400L)
        );
        RetentionCleanupService service = service(retention(true, true));

        RetentionCleanupResultVo result = service.previewCleanup();

        assertThat(result.dryRun()).isTrue();
        assertThat(result.candidateJobCount()).isEqualTo(1);
        assertThat(result.deletedJobCount()).isZero();
        assertThat(result.deletedVideoCount()).isZero();
        assertThat(result.deletedObjectCount()).isZero();
        assertThat(result.skippedObjectCount()).isEqualTo(2);
        assertThat(objectStorageService.deletedObjectKeys).isEmpty();
        assertThat(jobRepository.findById("retention-job-dry")).isPresent();
        assertThat(videoRepository.findById("retention-video-dry")).isPresent();
        assertThat(artifactRepository.findByJobId("retention-job-dry")).hasSize(1);
    }

    @Test
    void disabledRetentionReturnsEmptyDryRunResult() {
        createJobWithArtifact(
                "retention-video-disabled",
                "retention-job-disabled",
                LocalizationJobStatus.COMPLETED,
                NOW.minusSeconds(10 * 86400L)
        );
        RetentionCleanupService service = service(retention(false, false));

        RetentionCleanupResultVo result = service.runCleanup();

        assertThat(result.dryRun()).isTrue();
        assertThat(result.candidateJobCount()).isZero();
        assertThat(result.deletedJobCount()).isZero();
        assertThat(result.deletedVideoCount()).isZero();
        assertThat(result.deletedObjectCount()).isZero();
        assertThat(result.skippedObjectCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(jobRepository.findById("retention-job-disabled")).isPresent();
    }

    @Test
    void executeDeletesObjectsDependentRowsJobAndOrphanedVideo() {
        createJobWithArtifact(
                "retention-video-execute",
                "retention-job-execute",
                LocalizationJobStatus.COMPLETED,
                NOW.minusSeconds(10 * 86400L)
        );
        RetentionCleanupService service = service(retention(true, false));

        RetentionCleanupResultVo result = service.runCleanup();

        assertThat(result.dryRun()).isFalse();
        assertThat(result.candidateJobCount()).isEqualTo(1);
        assertThat(result.deletedJobCount()).isEqualTo(1);
        assertThat(result.deletedVideoCount()).isEqualTo(1);
        assertThat(result.deletedObjectCount()).isEqualTo(2);
        assertThat(result.skippedObjectCount()).isZero();
        assertThat(result.failureCount()).isZero();
        assertThat(objectStorageService.deletedObjectKeys)
                .containsExactlyInAnyOrder(
                        "source-videos/retention-video-execute/source.mp4",
                        "job-artifacts/retention-job-execute/subtitles.vtt"
                );
        assertThat(jobRepository.findById("retention-job-execute")).isEmpty();
        assertThat(videoRepository.findById("retention-video-execute")).isEmpty();
        assertThat(artifactRepository.findByJobId("retention-job-execute")).isEmpty();
        assertThat(dispatchEventRepository.findLatestByJobId("retention-job-execute")).isEmpty();
        assertThat(timelineEventRepository.findByJobId("retention-job-execute")).isEmpty();
    }

    @Test
    void executeKeepsSharedVideoWhenAnotherJobStillReferencesIt() {
        Instant old = NOW.minusSeconds(10 * 86400L);
        videoRepository.save(new VideoRecord(
                "retention-video-shared",
                "shared.mp4",
                "video/mp4",
                100L,
                30,
                "source-videos/retention-video-shared/source.mp4",
                MediaUploadStatus.UPLOADED,
                old
        ));
        createJobForExistingVideo("retention-video-shared", "retention-job-shared-old", LocalizationJobStatus.COMPLETED, old);
        createJobForExistingVideo("retention-video-shared", "retention-job-shared-new", LocalizationJobStatus.PROCESSING, NOW);
        artifactRepository.save(artifact("retention-artifact-shared-old", "retention-job-shared-old"));
        RetentionCleanupService service = service(retention(true, false));

        RetentionCleanupResultVo result = service.runCleanup();

        assertThat(result.deletedJobCount()).isEqualTo(1);
        assertThat(result.deletedVideoCount()).isZero();
        assertThat(objectStorageService.deletedObjectKeys)
                .containsExactly("job-artifacts/retention-job-shared-old/subtitles.vtt");
        assertThat(jobRepository.findById("retention-job-shared-old")).isEmpty();
        assertThat(jobRepository.findById("retention-job-shared-new")).isPresent();
        assertThat(videoRepository.findById("retention-video-shared")).isPresent();
    }

    @Test
    void objectDeleteFailureLeavesDatabaseRowsForThatJob() {
        createJobWithArtifact(
                "retention-video-failure",
                "retention-job-failure",
                LocalizationJobStatus.FAILED,
                NOW.minusSeconds(10 * 86400L)
        );
        objectStorageService.failingObjectKeys.add("job-artifacts/retention-job-failure/subtitles.vtt");
        RetentionCleanupService service = service(retention(true, false));

        RetentionCleanupResultVo result = service.runCleanup();

        assertThat(result.candidateJobCount()).isEqualTo(1);
        assertThat(result.deletedJobCount()).isZero();
        assertThat(result.deletedVideoCount()).isZero();
        assertThat(result.deletedObjectCount()).isEqualTo(1);
        assertThat(result.failureCount()).isEqualTo(1);
        assertThat(jobRepository.findById("retention-job-failure")).isPresent();
        assertThat(videoRepository.findById("retention-video-failure")).isPresent();
        assertThat(artifactRepository.findByJobId("retention-job-failure")).hasSize(1);
    }

    private RetentionCleanupService service(LinguaFrameProperties.Retention retention) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getRetention().setEnabled(retention.isEnabled());
        properties.getRetention().setDryRun(retention.isDryRun());
        properties.getRetention().setCompletedJobTtlDays(retention.getCompletedJobTtlDays());
        properties.getRetention().setFailedJobTtlDays(retention.getFailedJobTtlDays());
        properties.getRetention().setCancelledJobTtlDays(retention.getCancelledJobTtlDays());
        properties.getRetention().setCleanupBatchSize(retention.getCleanupBatchSize());
        return new RetentionCleanupServiceImpl(
                properties,
                jobRepository,
                artifactRepository,
                dispatchEventRepository,
                timelineEventRepository,
                videoRepository,
                objectStorageService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private LinguaFrameProperties.Retention retention(boolean enabled, boolean dryRun) {
        LinguaFrameProperties.Retention retention = new LinguaFrameProperties.Retention();
        retention.setEnabled(enabled);
        retention.setDryRun(dryRun);
        retention.setCompletedJobTtlDays(7);
        retention.setFailedJobTtlDays(3);
        retention.setCancelledJobTtlDays(3);
        retention.setCleanupBatchSize(25);
        return retention;
    }

    private void createJobWithArtifact(
            String videoId,
            String jobId,
            LocalizationJobStatus status,
            Instant updatedAt
    ) {
        videoRepository.save(new VideoRecord(
                videoId,
                "source.mp4",
                "video/mp4",
                100L,
                30,
                "source-videos/" + videoId + "/source.mp4",
                MediaUploadStatus.UPLOADED,
                updatedAt
        ));
        createJobForExistingVideo(videoId, jobId, status, updatedAt);
        artifactRepository.save(artifact("artifact-" + jobId, jobId));
        dispatchEventRepository.save(new JobDispatchEventRecord(
                "dispatch-" + jobId,
                jobId,
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{}",
                JobDispatchEventStatus.DISPATCHED,
                0,
                updatedAt,
                null,
                updatedAt,
                updatedAt,
                updatedAt
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "timeline-" + jobId,
                jobId,
                LocalizationJobStage.COMPLETED,
                JobTimelineEventStatus.SUCCEEDED,
                "Terminal job.",
                null,
                null,
                updatedAt
        ));
    }

    private void createJobForExistingVideo(
            String videoId,
            String jobId,
            LocalizationJobStatus status,
            Instant updatedAt
    ) {
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                "zh-CN",
                status,
                updatedAt,
                null,
                status == LocalizationJobStatus.COMPLETED || status == LocalizationJobStatus.CANCELLED ? updatedAt : null,
                status == LocalizationJobStatus.FAILED ? updatedAt : null,
                status == LocalizationJobStatus.FAILED ? LocalizationJobStage.WORKER_SMOKE : null,
                status == LocalizationJobStatus.FAILED ? "failed" : null,
                0,
                updatedAt
        ));
    }

    private JobArtifactRecord artifact(String artifactId, String jobId) {
        return new JobArtifactRecord(
                artifactId,
                jobId,
                JobArtifactType.TARGET_SUBTITLE_VTT,
                "job-artifacts/" + jobId + "/subtitles.vtt",
                "subtitles.vtt",
                "text/vtt",
                10L,
                "abc123",
                false,
                null,
                NOW.minusSeconds(10)
        );
    }

    private static class RecordingObjectStorageService implements ObjectStorageService {

        private final List<String> deletedObjectKeys = new ArrayList<>();
        private final Set<String> failingObjectKeys = new HashSet<>();

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public void delete(String objectKey) {
            deletedObjectKeys.add(objectKey);
            if (failingObjectKeys.contains(objectKey)) {
                throw new IllegalStateException("Object storage delete failed.");
            }
        }
    }
}
