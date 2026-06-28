package com.linguaframe.media.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.enums.MediaUploadStatus;

import java.time.Instant;

public record MediaUploadVo(
        String videoId,
        String jobId,
        String filename,
        String contentType,
        long fileSizeBytes,
        Integer durationSeconds,
        String sourceObjectKey,
        MediaUploadStatus status,
        LocalizationJobStatus jobStatus,
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        int translationGlossaryEntryCount,
        String translationGlossaryHash,
        String subtitlePolishingMode,
        Instant createdAt
) {
    public MediaUploadVo(
            String videoId,
            String jobId,
            String filename,
            String contentType,
            long fileSizeBytes,
            Integer durationSeconds,
            String sourceObjectKey,
            MediaUploadStatus status,
            LocalizationJobStatus jobStatus,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset,
            Instant createdAt
    ) {
        this(
                videoId,
                jobId,
                filename,
                contentType,
                fileSizeBytes,
                durationSeconds,
                sourceObjectKey,
                status,
                jobStatus,
                targetLanguage,
                ttsVoice,
                translationStyle,
                subtitleStylePreset,
                0,
                "",
                "OFF",
                createdAt
        );
    }
}
