package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;

public record LocalizationJobRecord(
        String id,
        String videoId,
        String targetLanguage,
        LocalizationJobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant failedAt,
        LocalizationJobStage failureStage,
        String failureReason,
        int retryCount,
        Instant updatedAt
) {
    public LocalizationJobRecord(
            String id,
            String videoId,
            String targetLanguage,
            LocalizationJobStatus status,
            Instant createdAt
    ) {
        this(id, videoId, targetLanguage, status, createdAt, null, null, null, null, null, 0, createdAt);
    }
}
