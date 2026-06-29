package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DemoSessionCommandCenterVo(
        Instant generatedAt,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        String primaryCommand,
        DemoSessionCommandCenterRunVo focusRun,
        DemoSessionCommandCenterRunVo activeRun,
        DemoSessionCommandCenterRunVo recommendedCompletedRun,
        List<DemoSessionCommandCenterPhaseVo> phases,
        List<DemoSessionCommandCenterActionVo> actions,
        List<DemoSessionCommandCenterEvidenceVo> evidenceLinks,
        BigDecimal estimatedCostUsd,
        int modelCallCount,
        int failedModelCallCount,
        BigDecimal failureRatePercent,
        long averageLatencyMs,
        int providerCacheHitCount,
        List<String> safetyNotes
) {
}
