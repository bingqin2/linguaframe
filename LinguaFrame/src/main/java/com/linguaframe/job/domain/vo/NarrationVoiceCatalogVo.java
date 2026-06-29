package com.linguaframe.job.domain.vo;

import java.util.List;

public record NarrationVoiceCatalogVo(
        String provider,
        String defaultVoice,
        List<NarrationVoicePresetVo> presets,
        List<String> safetyNotes
) {
}
