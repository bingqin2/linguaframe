package com.linguaframe.media.domain.bo;

public record UploadCostEstimateOptionsBo(
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        String translationGlossary,
        String subtitlePolishingMode,
        String demoProfileId,
        String narrationScript
) {

    public UploadCostEstimateOptionsBo(
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            String subtitleStylePreset,
            String translationGlossary,
            String subtitlePolishingMode,
            String demoProfileId
    ) {
        this(
                targetLanguage,
                ttsVoice,
                translationStyle,
                subtitleStylePreset,
                translationGlossary,
                subtitlePolishingMode,
                demoProfileId,
                null
        );
    }

    public static UploadCostEstimateOptionsBo empty() {
        return new UploadCostEstimateOptionsBo(null, null, null, null, null, null, null, null);
    }
}
