package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record JobTimelineEventRecord(
        String id,
        String jobId,
        LocalizationJobStage stage,
        JobTimelineEventStatus status,
        String message,
        Long durationMs,
        String errorSummary,
        Instant occurredAt
) {
}
