package com.linguaframe.job.domain.dto;

import java.math.BigDecimal;

public record ImportNarrationScriptPackageSegmentDto(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String text,
        String voice,
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        Integer fadeDurationMs
) {
    public ImportNarrationScriptPackageSegmentDto(int index, BigDecimal startSeconds, BigDecimal endSeconds, String text, String voice) {
        this(index, startSeconds, endSeconds, text, voice, null, null, null);
    }
}
