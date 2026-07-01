package com.linguaframe.job.domain.dto;

public record CustomNarrationRenderPreflightDto(
        boolean generateNarratedVideo,
        boolean acknowledgeProviderCost,
        boolean acknowledgeVideoRender
) {
}
