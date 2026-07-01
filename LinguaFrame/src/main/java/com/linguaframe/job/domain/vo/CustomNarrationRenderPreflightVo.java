package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record CustomNarrationRenderPreflightVo(
        String jobId,
        String status,
        List<CustomNarrationRenderCheckVo> checks,
        int segmentCount,
        int characterCount,
        BigDecimal totalNarrationSeconds,
        String voiceSummary,
        String sceneBoardStatus,
        String renderReviewStatus,
        String evidenceStatus,
        String providerMode,
        boolean paidProvider,
        boolean generateNarratedVideo,
        boolean audioReady,
        boolean videoReady,
        List<String> requiredAcknowledgements,
        String safeNextCommand,
        List<String> safeRoutes,
        List<String> safetyNotes
) {
}
