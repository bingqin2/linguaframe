package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.math.BigDecimal;

public record JobComparisonJobVo(
        String jobId,
        String videoId,
        String targetLanguage,
        String demoProfileId,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        int translationGlossaryEntryCount,
        String translationGlossaryHash,
        String subtitlePolishingMode,
        LocalizationJobStatus status,
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
