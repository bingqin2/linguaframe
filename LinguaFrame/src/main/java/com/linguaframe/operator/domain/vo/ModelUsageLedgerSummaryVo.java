package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;

public record ModelUsageLedgerSummaryVo(
        String ledgerStatus,
        int jobCount,
        int modelCallCount,
        int failedModelCallCount,
        int providerCacheHitCount,
        int generatedArtifactCount,
        long totalLatencyMs,
        BigDecimal estimatedCostUsd,
        long averageLatencyMs,
        BigDecimal failureRatePercent,
        String recommendedNextAction
) {
}
