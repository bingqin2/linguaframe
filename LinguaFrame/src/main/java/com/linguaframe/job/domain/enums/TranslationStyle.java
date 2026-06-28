package com.linguaframe.job.domain.enums;

import org.springframework.util.StringUtils;

import java.util.Arrays;

public enum TranslationStyle {

    NATURAL("Translate into idiomatic, natural subtitles while preserving meaning and timing."),
    FORMAL("Use polished, presentation-safe wording while preserving meaning and timing."),
    CONCISE("Use concise subtitle wording that is easy to read quickly while preserving meaning.");

    private static final TranslationStyle DEFAULT = NATURAL;

    private final String promptInstruction;

    TranslationStyle(String promptInstruction) {
        this.promptInstruction = promptInstruction;
    }

    public String promptInstruction() {
        return promptInstruction;
    }

    public static TranslationStyle defaultStyle() {
        return DEFAULT;
    }

    public static TranslationStyle parse(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(style -> style.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported translation style: " + value));
    }
}
