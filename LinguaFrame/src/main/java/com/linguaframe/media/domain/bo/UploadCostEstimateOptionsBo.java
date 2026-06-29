package com.linguaframe.media.domain.bo;

public record UploadCostEstimateOptionsBo(
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        String translationGlossary,
        String subtitlePolishingMode,
        String demoProfileId
) {

    public static UploadCostEstimateOptionsBo empty() {
        return new UploadCostEstimateOptionsBo(null, null, null, null, null, null, null);
    }
}
