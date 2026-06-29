package com.linguaframe.job.domain.dto;

public record NarrationDemoRenderPreflightRequestDto(
        String presetId,
        boolean replaceExisting,
        boolean generateNarratedVideo
) {
}
