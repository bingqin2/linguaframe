package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DemoSessionCostControlSummaryVo(
        String ownerId,
        String ownershipScope,
        boolean estimatedCostTrackingEnabled,
        BigDecimal recentEstimatedCostUsd,
        BigDecimal dailyEstimatedCostUsd,
        LocalDate dailyBudgetDate,
        int modelCallCount,
        int failedModelCallCount,
        BigDecimal failureRatePercent,
        String recommendedNextAction
) {
}
