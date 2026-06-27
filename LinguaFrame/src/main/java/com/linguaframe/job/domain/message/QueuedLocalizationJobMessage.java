package com.linguaframe.job.domain.message;

import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record QueuedLocalizationJobMessage(
        String jobId,
        String videoId,
        String sourceObjectKey,
        String targetLanguage,
        String ttsVoice,
        Instant createdAt,
        LocalizationJobStage startStage
) {

    public QueuedLocalizationJobMessage {
        if (startStage == null) {
            startStage = LocalizationJobStage.WORKER_SMOKE;
        }
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, null, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            Instant createdAt,
            LocalizationJobStage startStage
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, null, createdAt, startStage);
    }
}
