package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record LocalizationJobSummaryVo(
        String jobId,
        String videoId,
        String filename,
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        int translationGlossaryEntryCount,
        String translationGlossaryHash,
        LocalizationJobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant failedAt,
        LocalizationJobStage failureStage,
        String failureReason,
        int retryCount,
        BigDecimal estimatedCostUsd
) {
    public LocalizationJobSummaryVo(
            String jobId,
            String videoId,
            String filename,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset,
            LocalizationJobStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant failedAt,
            LocalizationJobStage failureStage,
            String failureReason,
            int retryCount,
            BigDecimal estimatedCostUsd
    ) {
        this(
                jobId,
                videoId,
                filename,
                targetLanguage,
                ttsVoice,
                translationStyle,
                subtitleStylePreset,
                0,
                "",
                status,
                createdAt,
                startedAt,
                completedAt,
                failedAt,
                failureStage,
                failureReason,
                retryCount,
                estimatedCostUsd
        );
    }
}
