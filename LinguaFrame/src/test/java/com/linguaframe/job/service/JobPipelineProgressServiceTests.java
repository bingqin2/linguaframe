package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.service.impl.JobPipelineProgressServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobPipelineProgressServiceTests {

    private final JobPipelineProgressService service = new JobPipelineProgressServiceImpl();

    @Test
    void summarizesCompletedJobStageCountsAndSlowestStage() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");

        JobPipelineProgressVo result = service.summarize(job(LocalizationJobStatus.COMPLETED), List.of(
                event(LocalizationJobStage.WORKER_RECEIVED, JobTimelineEventStatus.STARTED, null, base),
                event(LocalizationJobStage.WORKER_SMOKE, JobTimelineEventStatus.SUCCEEDED, 100L, base.plusSeconds(1)),
                event(LocalizationJobStage.AUDIO_EXTRACTION, JobTimelineEventStatus.SUCCEEDED, 2500L, base.plusSeconds(2)),
                event(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT, JobTimelineEventStatus.CACHE_HIT, null, base.plusSeconds(3)),
                event(LocalizationJobStage.COMPLETED, JobTimelineEventStatus.SUCCEEDED, 3200L, base.plusSeconds(4))
        ));

        assertThat(result.totalStageCount()).isEqualTo(LocalizationJobStage.values().length);
        assertThat(result.completedStageCount()).isEqualTo(3);
        assertThat(result.cacheHitStageCount()).isEqualTo(1);
        assertThat(result.failedStageCount()).isZero();
        assertThat(result.terminal()).isTrue();
        assertThat(result.currentStage()).isEqualTo(LocalizationJobStage.COMPLETED);
        assertThat(result.totalMeasuredDurationMs()).isEqualTo(5800L);
        assertThat(result.slowestStage()).isEqualTo(LocalizationJobStage.COMPLETED);
        assertThat(result.slowestStageDurationMs()).isEqualTo(3200L);
        assertThat(result.stages())
                .filteredOn(stage -> stage.stage() == LocalizationJobStage.AUDIO_EXTRACTION)
                .singleElement()
                .satisfies(stage -> {
                    assertThat(stage.status()).isEqualTo(JobTimelineEventStatus.SUCCEEDED);
                    assertThat(stage.startedAt()).isNull();
                    assertThat(stage.finishedAt()).isEqualTo(base.plusSeconds(2));
                    assertThat(stage.durationMs()).isEqualTo(2500L);
                });
    }

    @Test
    void summarizesFailedJobAndUsesLatestDuplicateStageEvent() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");

        JobPipelineProgressVo result = service.summarize(job(LocalizationJobStatus.FAILED), List.of(
                event(LocalizationJobStage.TARGET_SUBTITLE_EXPORT, JobTimelineEventStatus.STARTED, null, base),
                event(LocalizationJobStage.TARGET_SUBTITLE_EXPORT, JobTimelineEventStatus.FAILED, 900L, base.plusSeconds(1)),
                event(LocalizationJobStage.TARGET_SUBTITLE_EXPORT, JobTimelineEventStatus.FAILED, 1200L, base.plusSeconds(2))
        ));

        assertThat(result.terminal()).isTrue();
        assertThat(result.currentStage()).isEqualTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        assertThat(result.failedStageCount()).isEqualTo(1);
        assertThat(result.totalMeasuredDurationMs()).isEqualTo(1200L);
        assertThat(result.slowestStage()).isEqualTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        assertThat(result.stages())
                .filteredOn(stage -> stage.stage() == LocalizationJobStage.TARGET_SUBTITLE_EXPORT)
                .singleElement()
                .satisfies(stage -> {
                    assertThat(stage.status()).isEqualTo(JobTimelineEventStatus.FAILED);
                    assertThat(stage.startedAt()).isEqualTo(base);
                    assertThat(stage.finishedAt()).isEqualTo(base.plusSeconds(2));
                    assertThat(stage.durationMs()).isEqualTo(1200L);
                });
    }

    @Test
    void summarizesProcessingJobCurrentStartedStage() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");

        JobPipelineProgressVo result = service.summarize(job(LocalizationJobStatus.PROCESSING), List.of(
                event(LocalizationJobStage.WORKER_SMOKE, JobTimelineEventStatus.SUCCEEDED, 100L, base),
                event(LocalizationJobStage.AUDIO_EXTRACTION, JobTimelineEventStatus.STARTED, null, base.plusSeconds(1))
        ));

        assertThat(result.terminal()).isFalse();
        assertThat(result.currentStage()).isEqualTo(LocalizationJobStage.AUDIO_EXTRACTION);
        assertThat(result.completedStageCount()).isEqualTo(1);
        assertThat(result.totalMeasuredDurationMs()).isEqualTo(100L);
        assertThat(result.stages())
                .filteredOn(stage -> stage.stage() == LocalizationJobStage.AUDIO_EXTRACTION)
                .singleElement()
                .satisfies(stage -> {
                    assertThat(stage.status()).isEqualTo(JobTimelineEventStatus.STARTED);
                    assertThat(stage.startedAt()).isEqualTo(base.plusSeconds(1));
                    assertThat(stage.finishedAt()).isNull();
                    assertThat(stage.durationMs()).isNull();
                });
    }

    @Test
    void summarizesSkippedAndEmptyTimelineJobs() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");

        JobPipelineProgressVo skipped = service.summarize(job(LocalizationJobStatus.CANCELLED), List.of(
                event(LocalizationJobStage.SUBTITLE_BURN_IN, JobTimelineEventStatus.SKIPPED, null, base)
        ));
        JobPipelineProgressVo empty = service.summarize(job(LocalizationJobStatus.QUEUED), List.of());

        assertThat(skipped.skippedStageCount()).isEqualTo(1);
        assertThat(skipped.currentStage()).isEqualTo(LocalizationJobStage.SUBTITLE_BURN_IN);
        assertThat(skipped.terminal()).isTrue();
        assertThat(empty.currentStage()).isNull();
        assertThat(empty.terminal()).isFalse();
        assertThat(empty.totalMeasuredDurationMs()).isZero();
        assertThat(empty.stages()).hasSize(LocalizationJobStage.values().length);
    }

    private LocalizationJobRecord job(LocalizationJobStatus status) {
        Instant createdAt = Instant.parse("2026-06-28T08:00:00Z");
        return new LocalizationJobRecord(
                "job-progress",
                "video-progress",
                "zh-CN",
                status,
                createdAt
        );
    }

    private JobTimelineEventRecord event(
            LocalizationJobStage stage,
            JobTimelineEventStatus status,
            Long durationMs,
            Instant occurredAt
    ) {
        return new JobTimelineEventRecord(
                stage + "-" + status + "-" + occurredAt,
                "job-progress",
                stage,
                status,
                stage.name() + " " + status.name(),
                durationMs,
                status == JobTimelineEventStatus.FAILED ? "stage failed" : null,
                occurredAt
        );
    }
}
