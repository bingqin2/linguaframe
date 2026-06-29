package com.linguaframe.job.domain.vo;

public record NarrationVoicePresetVo(
        String voice,
        String label,
        String provider,
        boolean defaultPreset,
        String description
) {
}
