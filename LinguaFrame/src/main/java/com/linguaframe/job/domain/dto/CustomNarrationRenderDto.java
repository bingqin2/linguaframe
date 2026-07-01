package com.linguaframe.job.domain.dto;

public record CustomNarrationRenderDto(
        boolean generateNarratedVideo,
        boolean acknowledgeProviderCost,
        boolean acknowledgeVideoRender
) {
}
