package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record JobStageProgressVo(
        LocalizationJobStage stage,
        JobTimelineEventStatus status,
        Instant startedAt,
        Instant finishedAt,
        Long durationMs,
        String message
) {
}
