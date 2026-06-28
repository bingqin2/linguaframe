package com.linguaframe.job.domain.enums;

import org.springframework.util.StringUtils;

import java.util.Arrays;

public enum SubtitleStylePreset {

    STANDARD("Fontsize=20,Outline=2,Shadow=0,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000"),
    LARGE("Fontsize=28,Outline=2,Shadow=0,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000"),
    HIGH_CONTRAST("Fontsize=24,Outline=3,Shadow=1,PrimaryColour=&H00FFFFFF,OutlineColour=&H00000000,BackColour=&H80000000");

    private static final SubtitleStylePreset DEFAULT = STANDARD;

    private final String ffmpegForceStyle;

    SubtitleStylePreset(String ffmpegForceStyle) {
        this.ffmpegForceStyle = ffmpegForceStyle;
    }

    public String ffmpegForceStyle() {
        return ffmpegForceStyle;
    }

    public static SubtitleStylePreset defaultPreset() {
        return DEFAULT;
    }

    public static SubtitleStylePreset parse(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(preset -> preset.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported subtitle style preset: " + value));
    }
}
