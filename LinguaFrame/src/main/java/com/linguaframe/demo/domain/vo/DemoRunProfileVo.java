package com.linguaframe.demo.domain.vo;

public record DemoRunProfileVo(
        String id,
        String label,
        String description,
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        String subtitlePolishingMode,
        String translationGlossary
) {
}
