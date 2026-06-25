package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;

import java.time.Instant;

public record JobDispatchEventRecord(
        String id,
        String jobId,
        JobDispatchEventType eventType,
        String payloadJson,
        JobDispatchEventStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant dispatchedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
