package com.linguaframe.operator.repository;

import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.repository.ModelCallRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OperatorDashboardRepositoryTests {

    @Autowired
    private OperatorDashboardRepository dashboardRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private ModelCallRepository modelCallRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

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
    void buildsDashboardFromDurableJobModelCallArtifactAndTimelineTables() {
        Instant base = Instant.parse("2026-06-27T06:00:00Z");
        createJob("video-queued", "dashboard-job-queued", "queued.mp4", LocalizationJobStatus.QUEUED, base);
        createJob("video-processing", "dashboard-job-processing", "processing.mp4", LocalizationJobStatus.PROCESSING, base.plusSeconds(10));
        createJob("video-completed", "dashboard-job-completed", "completed.mp4", LocalizationJobStatus.COMPLETED, base.plusSeconds(20));
        createJob("video-failed-old", "dashboard-job-failed-old", "old-fail.mp4", LocalizationJobStatus.FAILED, base.plusSeconds(30));
        createJob("video-failed-new", "dashboard-job-failed-new", "new-fail.mp4", LocalizationJobStatus.FAILED, base.plusSeconds(40));
        jobRepository.markFailed(
                "dashboard-job-failed-old",
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                "older failure",
                base.plusSeconds(31)
        );
        jobRepository.markFailed(
                "dashboard-job-failed-new",
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                "newer failure",
                base.plusSeconds(41)
        );
        saveModelCall("dashboard-model-call-ok", "dashboard-job-completed", ModelCallStatus.SUCCEEDED,
                new BigDecimal("0.00010000"), 120L, base.plusSeconds(21));
        saveModelCall("dashboard-model-call-failed", "dashboard-job-failed-new", ModelCallStatus.FAILED,
                new BigDecimal("0.00005000"), 80L, base.plusSeconds(42));
        saveArtifact("dashboard-generated-artifact", "dashboard-job-completed", false, null, base.plusSeconds(22));
        saveArtifact("dashboard-reused-artifact", "dashboard-job-completed", true, "source-artifact", base.plusSeconds(23));
        saveProviderCacheHit("dashboard-provider-cache-hit", "dashboard-job-completed", base.plusSeconds(24));
        saveStageTiming("dashboard-stage-audio", "dashboard-job-completed", LocalizationJobStage.AUDIO_EXTRACTION,
                JobTimelineEventStatus.SUCCEEDED, 2400L, base.plusSeconds(25));
        saveStageTiming("dashboard-stage-translation", "dashboard-job-completed", LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.SUCCEEDED, 700L, base.plusSeconds(26));
        saveStageTiming("dashboard-stage-failed", "dashboard-job-failed-new", LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                JobTimelineEventStatus.FAILED, 1800L, base.plusSeconds(43));

        var dashboard = dashboardRepository.fetchDashboard();

        assertThat(dashboard.statusCounts())
                .filteredOn(count -> count.status() == LocalizationJobStatus.FAILED)
                .singleElement()
                .satisfies(count -> assertThat(count.count()).isEqualTo(2));
        assertThat(dashboard.statusCounts())
                .filteredOn(count -> count.status() == LocalizationJobStatus.CANCELLED)
                .singleElement()
                .satisfies(count -> assertThat(count.count()).isZero());
        assertThat(dashboard.recentFailures())
                .extracting("jobId")
                .containsExactly("dashboard-job-failed-new", "dashboard-job-failed-old");
        assertThat(dashboard.recentFailures().getFirst())
                .satisfies(failure -> {
                    assertThat(failure.filename()).isEqualTo("new-fail.mp4");
                    assertThat(failure.failureStage()).isEqualTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION);
                    assertThat(failure.failureReason()).isEqualTo("newer failure");
                });
        assertThat(dashboard.modelCalls().modelCallCount()).isEqualTo(2);
        assertThat(dashboard.modelCalls().failedModelCallCount()).isEqualTo(1);
        assertThat(dashboard.modelCalls().totalLatencyMs()).isEqualTo(200L);
        assertThat(dashboard.modelCalls().estimatedCostUsd()).isEqualByComparingTo("0.00015000");
        assertThat(dashboard.cache().artifactCacheHitCount()).isEqualTo(1);
        assertThat(dashboard.cache().generatedArtifactCount()).isEqualTo(1);
        assertThat(dashboard.cache().providerCacheHitCount()).isEqualTo(1);
        assertThat(dashboard.stageTimings())
                .extracting("stage")
                .containsExactly(
                        LocalizationJobStage.AUDIO_EXTRACTION,
                        LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                        LocalizationJobStage.TARGET_SUBTITLE_EXPORT
                );
        assertThat(dashboard.stageTimings().getFirst())
                .satisfies(timing -> {
                    assertThat(timing.completedEventCount()).isEqualTo(1);
                    assertThat(timing.failedEventCount()).isZero();
                    assertThat(timing.averageDurationMs()).isEqualTo(2400L);
                    assertThat(timing.maxDurationMs()).isEqualTo(2400L);
                    assertThat(timing.latestDurationMs()).isEqualTo(2400L);
                });
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

    private void saveModelCall(
            String id,
            String jobId,
            ModelCallStatus status,
            BigDecimal estimatedCostUsd,
            long latencyMs,
            Instant createdAt
    ) {
        modelCallRepository.save(new ModelCallRecord(
                id,
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                status,
                latencyMs,
                100,
                50,
                null,
                null,
                "input",
                status == ModelCallStatus.SUCCEEDED ? "output" : null,
                "demo-owner",
                estimatedCostUsd,
                status == ModelCallStatus.FAILED ? "provider failed" : null,
                createdAt
        ));
    }

    private void saveArtifact(
            String id,
            String jobId,
            boolean cacheHit,
            String sourceArtifactId,
            Instant createdAt
    ) {
        artifactRepository.save(new JobArtifactRecord(
                id,
                jobId,
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/" + jobId + "/" + id + "/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                456L,
                "hash-" + id,
                cacheHit,
                sourceArtifactId,
                createdAt
        ));
    }

    private void saveProviderCacheHit(String id, String jobId, Instant occurredAt) {
        timelineEventRepository.save(new JobTimelineEventRecord(
                id,
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.CACHE_HIT,
                "Reused cached TRANSLATION provider result.",
                null,
                null,
                occurredAt
        ));
    }

    private void saveStageTiming(
            String id,
            String jobId,
            LocalizationJobStage stage,
            JobTimelineEventStatus status,
            long durationMs,
            Instant occurredAt
    ) {
        timelineEventRepository.save(new JobTimelineEventRecord(
                id,
                jobId,
                stage,
                status,
                stage.name() + " " + status.name(),
                durationMs,
                status == JobTimelineEventStatus.FAILED ? "stage failed" : null,
                occurredAt
        ));
    }
}
