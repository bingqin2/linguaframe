package com.linguaframe.operator.domain.vo;

import java.util.List;

public record OperatorDashboardVo(
        String ownerId,
        String ownershipScope,
        List<OperatorJobStatusCountVo> statusCounts,
        List<OperatorRecentFailureVo> recentFailures,
        OperatorModelCallSummaryVo modelCalls,
        OperatorCacheSummaryVo cache,
        List<OperatorStageTimingVo> stageTimings
) {
    public OperatorDashboardVo(
            List<OperatorJobStatusCountVo> statusCounts,
            List<OperatorRecentFailureVo> recentFailures,
            OperatorModelCallSummaryVo modelCalls,
            OperatorCacheSummaryVo cache,
            List<OperatorStageTimingVo> stageTimings
    ) {
        this(
                "demo-owner",
                "CONFIGURED_DEMO_OWNER",
                statusCounts,
                recentFailures,
                modelCalls,
                cache,
                stageTimings
        );
    }
}
