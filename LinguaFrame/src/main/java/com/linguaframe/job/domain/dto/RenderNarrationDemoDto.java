package com.linguaframe.job.domain.dto;

public record RenderNarrationDemoDto(
        String presetId,
        boolean replaceExisting,
        boolean generateNarratedVideo
) {
}
