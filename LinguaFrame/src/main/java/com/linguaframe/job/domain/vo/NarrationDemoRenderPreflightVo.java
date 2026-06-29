package com.linguaframe.job.domain.vo;

import java.util.List;

public record NarrationDemoRenderPreflightVo(
        String jobId,
        String presetId,
        String status,
        List<NarrationDemoRenderPreflightCheckVo> checks,
        int estimatedSegmentCount,
        int estimatedCharacterCount,
        String providerMode,
        boolean paidProvider,
        int existingWorkspaceSegmentCount,
        boolean generateNarratedVideo,
        List<String> requiredConfirmations,
        String safeNextCommand,
        List<String> evidenceRoutes
) {
}
