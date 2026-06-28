package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobPipelineProgressVo;
import com.linguaframe.job.domain.vo.JobStageProgressVo;
import com.linguaframe.job.service.JobPipelineProgressService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class JobPipelineProgressServiceImpl implements JobPipelineProgressService {

    @Override
    public JobPipelineProgressVo summarize(
            LocalizationJobRecord record,
            List<JobTimelineEventRecord> timelineEvents
    ) {
        Map<LocalizationJobStage, StageAccumulator> accumulators = new EnumMap<>(LocalizationJobStage.class);
        for (LocalizationJobStage stage : LocalizationJobStage.values()) {
            accumulators.put(stage, new StageAccumulator(stage));
        }
        timelineEvents.stream()
                .sorted(Comparator.comparing(JobTimelineEventRecord::occurredAt))
                .forEach(event -> accumulators.get(event.stage()).accept(event));

        List<JobStageProgressVo> stages = new ArrayList<>();
        int completed = 0;
        int failed = 0;
        int skipped = 0;
        int cacheHit = 0;
        long totalDuration = 0L;
        LocalizationJobStage currentStage = null;
        LocalizationJobStage slowestStage = null;
        Long slowestDuration = null;

        for (LocalizationJobStage stage : LocalizationJobStage.values()) {
            JobStageProgressVo progress = accumulators.get(stage).toProgress();
            stages.add(progress);
            if (progress.status() != null) {
                currentStage = progress.stage();
            }
            if (progress.status() == JobTimelineEventStatus.SUCCEEDED) {
                completed++;
            } else if (progress.status() == JobTimelineEventStatus.FAILED) {
                failed++;
            } else if (progress.status() == JobTimelineEventStatus.SKIPPED) {
                skipped++;
            } else if (progress.status() == JobTimelineEventStatus.CACHE_HIT) {
                cacheHit++;
            }
            if (progress.durationMs() != null) {
                totalDuration += progress.durationMs();
                if (slowestDuration == null || progress.durationMs() > slowestDuration) {
                    slowestDuration = progress.durationMs();
                    slowestStage = progress.stage();
                }
            }
        }

        return new JobPipelineProgressVo(
                LocalizationJobStage.values().length,
                completed,
                failed,
                skipped,
                cacheHit,
                currentStage,
                isTerminal(record.status()),
                totalDuration,
                slowestStage,
                slowestDuration,
                List.copyOf(stages)
        );
    }

    private boolean isTerminal(LocalizationJobStatus status) {
        return status == LocalizationJobStatus.COMPLETED
                || status == LocalizationJobStatus.FAILED
                || status == LocalizationJobStatus.CANCELLED;
    }

    private static final class StageAccumulator {

        private final LocalizationJobStage stage;
        private Instant startedAt;
        private Instant finishedAt;
        private JobTimelineEventStatus status;
        private Long durationMs;
        private String message;

        private StageAccumulator(LocalizationJobStage stage) {
            this.stage = stage;
        }

        private void accept(JobTimelineEventRecord event) {
            if (event.status() == JobTimelineEventStatus.STARTED) {
                startedAt = event.occurredAt();
            }
            status = event.status();
            message = event.errorSummary() == null ? event.message() : event.errorSummary();
            if (isTerminalStageStatus(event.status())) {
                finishedAt = event.occurredAt();
                durationMs = event.durationMs();
            }
        }

        private JobStageProgressVo toProgress() {
            return new JobStageProgressVo(stage, status, startedAt, finishedAt, durationMs, message);
        }

        private boolean isTerminalStageStatus(JobTimelineEventStatus status) {
            return status == JobTimelineEventStatus.SUCCEEDED
                    || status == JobTimelineEventStatus.FAILED
                    || status == JobTimelineEventStatus.SKIPPED
                    || status == JobTimelineEventStatus.CACHE_HIT;
        }
    }
}
