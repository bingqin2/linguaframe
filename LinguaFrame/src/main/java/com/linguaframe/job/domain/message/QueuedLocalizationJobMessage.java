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
        String subtitlePolishingMode,
        String demoProfileId,
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
        if (subtitlePolishingMode == null || subtitlePolishingMode.isBlank()) {
            subtitlePolishingMode = "OFF";
        } else {
            subtitlePolishingMode = subtitlePolishingMode.trim().toUpperCase();
        }
        if (demoProfileId != null) {
            demoProfileId = demoProfileId.trim();
            if (demoProfileId.isBlank()) {
                demoProfileId = null;
            }
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
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, "STANDARD", "[]", "", 0, "OFF", null, createdAt, startStage);
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
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, "NATURAL", "STANDARD", "[]", "", 0, "OFF", null, createdAt, startStage);
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
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, "[]", "", 0, "OFF", null, createdAt, LocalizationJobStage.WORKER_SMOKE);
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
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, translationGlossaryJson, translationGlossaryHash, translationGlossaryEntryCount, "OFF", null, createdAt, LocalizationJobStage.WORKER_SMOKE);
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
            String subtitlePolishingMode,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, translationGlossaryJson, translationGlossaryHash, translationGlossaryEntryCount, subtitlePolishingMode, null, createdAt, LocalizationJobStage.WORKER_SMOKE);
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
            String subtitlePolishingMode,
            String demoProfileId,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, subtitleStylePreset, translationGlossaryJson, translationGlossaryHash, translationGlossaryEntryCount, subtitlePolishingMode, demoProfileId, createdAt, LocalizationJobStage.WORKER_SMOKE);
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
        this(jobId, videoId, sourceObjectKey, targetLanguage, ttsVoice, translationStyle, "STANDARD", "[]", "", 0, "OFF", null, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            Instant createdAt
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, null, "NATURAL", "STANDARD", "[]", "", 0, "OFF", null, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    public QueuedLocalizationJobMessage(
            String jobId,
            String videoId,
            String sourceObjectKey,
            String targetLanguage,
            Instant createdAt,
            LocalizationJobStage startStage
    ) {
        this(jobId, videoId, sourceObjectKey, targetLanguage, null, "NATURAL", "STANDARD", "[]", "", 0, "OFF", null, createdAt, startStage);
    }
}
