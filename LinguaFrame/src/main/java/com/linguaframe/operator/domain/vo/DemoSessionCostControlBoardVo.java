package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoSessionCostControlBoardVo(
        Instant generatedAt,
        String overallStatus,
        DemoSessionCostControlSummaryVo summary,
        List<DemoSessionCostControlBudgetVo> budgets,
        List<DemoSessionCostControlJobVo> jobs,
        List<DemoSessionCostControlOperationVo> operations,
        List<DemoSessionCostControlCheckVo> checks,
        DemoSessionCostControlActionVo primaryAction,
        List<DemoSessionCostControlLinkVo> links,
        List<String> safetyNotes,
        String markdown
) {
}
