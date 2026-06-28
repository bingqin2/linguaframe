package com.linguaframe.job.domain.message;

import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record QueuedLocalizationJobMessage(
        String jobId,
        String videoId,
        String sourceObjectKey,
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        String translationGlossaryJson,
        String translationGlossaryHash,
        int translationGlossaryEntryCount,
        Instant createdAt,
        LocalizationJobStage startStage
) {

    public QueuedLocalizationJobMessage {
        if (translationStyle == null || translationStyle.isBlank()) {
            translationStyle = "NATURAL";
        } else {
            translationStyle = translationStyle.trim().toUpperCase();
        }
        if (subtitleStylePreset == null || subtitleStylePreset.isBlank()) {
            subtitleStylePreset = "STANDARD";
        } else {
            subtitleStylePreset = subtitleStylePreset.trim().toUpperCase();
        }
        if (translationGlossaryJson == null || translationGlossaryJson.isBlank()) {
            translationGlossaryJson = "[]";
        }
        if (translationGlossaryHash == null) {
            translationGlossaryHash = "";
        } else {
            translationGlossaryHash = translationGlossaryHash.trim();
        }
        if (translationGlossaryEntryCount < 0) {
            translationGlossaryEntryCount = 0;
        }
        if (startStage == null) {
            startStage = LocalizationJobStage.WORKER_SMOKE;
        }
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            Instant createdAt,
            LocalizationJobStage startStage
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, "STANDARD", "[]", "", 0, createdAt, startStage);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            String ttsVoice,
            Instant createdAt,
            LocalizationJobStage startStage
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, "NATURAL", "STANDARD", "[]", "", 0, createdAt, startStage);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, "[]", "", 0, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset,
            String translationGlossaryJson,
            String translationGlossaryHash,
            int translationGlossaryEntryCount,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, translationGlossaryJson, translationGlossaryHash, translationGlossaryEntryCount, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, "STANDARD", "[]", "", 0, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, null, "NATURAL", "STANDARD", "[]", "", 0, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            Instant createdAt,
            LocalizationJobStage startStage
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, null, "NATURAL", "STANDARD", "[]", "", 0, createdAt, startStage);
    }
}
