package com.linguaframe.job.domain.vo;

public record CustomNarrationRenderHandoffVo(
        String jobId,
        String status,
        String outputPlan,
        int segmentCount,
        int characterCount,
        boolean audioReady,
        boolean videoReady,
        String reportRoute,
        String renderRoute,
        String evidenceRoute,
        String deliveryPackageRoute,
        String nextAction
) {
}
