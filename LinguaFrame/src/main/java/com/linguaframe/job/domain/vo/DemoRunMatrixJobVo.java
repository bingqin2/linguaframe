package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record DemoRunMatrixJobVo(
        String jobId,
        String videoId,
        String filename,
        String targetLanguage,
        String demoProfileId,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        int translationGlossaryEntryCount,
        String translationGlossaryHash,
        String subtitlePolishingMode,
        LocalizationJobStatus status,
        Instant createdAt,
        Instant completedAt,
        String failureStage,
        String failureReason,
        int retryCount,
        Integer qualityScore,
        String qualityVerdict,
        int modelCallCount,
        int failedModelCallCount,
        BigDecimal estimatedCostUsd,
        int artifactCacheHitCount,
        int generatedArtifactCount,
        int providerCacheHitCount,
        boolean handoffReady
) {
}
