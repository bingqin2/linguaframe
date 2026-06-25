package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record JobTimelineEventVo(
        String id,
        LocalizationJobStage stage,
        JobTimelineEventStatus status,
        String message,
        Long durationMs,
        String errorSummary,
        Instant occurredAt
) {
}
