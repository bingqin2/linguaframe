package com.linguaframe.job.domain.enums;

public enum SubtitlePolishingMode {

    OFF,
    BALANCED,
    STRICT;

    public static SubtitlePolishingMode parse(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        for (SubtitlePolishingMode mode : values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unsupported subtitle polishing mode: " + value + ".");
    }
}
