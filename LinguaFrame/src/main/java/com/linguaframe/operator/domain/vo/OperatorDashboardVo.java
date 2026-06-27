package com.linguaframe.operator.domain.vo;

import java.util.List;

public record OperatorDashboardVo(
        List<OperatorJobStatusCountVo> statusCounts,
        List<OperatorRecentFailureVo> recentFailures,
        OperatorModelCallSummaryVo modelCalls,
        OperatorCacheSummaryVo cache
) {
}
