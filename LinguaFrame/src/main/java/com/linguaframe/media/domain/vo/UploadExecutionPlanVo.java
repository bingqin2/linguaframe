package com.linguaframe.media.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record UploadExecutionPlanVo(
        String overallStatus,
        String recommendedNextAction,
        String filename,
        String contentType,
        long fileSizeBytes,
        long maxFileSizeBytes,
        Integer durationSeconds,
        int maxDurationSeconds,
        boolean valid,
        String validationCode,
        String validationMessage,
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        int translationGlossaryEntryCount,
        String translationGlossaryHash,
        String subtitlePolishingMode,
        String demoProfileId,
        BigDecimal estimatedCostUsdLower,
        BigDecimal estimatedCostUsd,
        BigDecimal estimatedCostUsdUpper,
        int estimatedDurationSecondsLower,
        int estimatedDurationSecondsUpper,
        List<UploadExecutionPlanStageVo> stages,
        List<UploadExecutionPlanGateVo> gates,
        List<UploadExecutionPlanCommandVo> commands,
        UploadSourceReuseVo sourceReuse,
        UploadSourceReuseDecisionVo sourceReuseDecision,
        List<String> cacheNotes,
        List<String> safetyNotes
) {
}
