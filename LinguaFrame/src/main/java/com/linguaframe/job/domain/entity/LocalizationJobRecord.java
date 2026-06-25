package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;

public record LocalizationJobRecord(
        String id,
        String videoId,
        String targetLanguage,
        LocalizationJobStatus status,
        Instant createdAt
) {
}
