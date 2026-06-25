package com.linguaframe.job.domain.message;

import java.time.Instant;

public record QueuedLocalizationJobMessage(
        String jobId,
        String videoId,
        String sourceObjectKey,
        String targetLanguage,
        Instant createdAt
) {
}
