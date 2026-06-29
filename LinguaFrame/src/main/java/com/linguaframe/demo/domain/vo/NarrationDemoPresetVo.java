package com.linguaframe.demo.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationDemoPresetVo(
        String id,
        String label,
        String description,
        String profileId,
        String sampleIdHint,
        String targetLanguage,
        String voiceSummary,
        int segmentCount,
        int totalCharacterCount,
        BigDecimal timeSpanSeconds,
        NarrationDemoPresetMixSettingsVo mixSettings,
        List<NarrationDemoPresetSegmentVo> segments,
        List<String> safetyNotes
) {
}
