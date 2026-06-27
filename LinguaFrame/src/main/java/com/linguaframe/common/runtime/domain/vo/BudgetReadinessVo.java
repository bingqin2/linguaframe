package com.linguaframe.common.runtime.domain.vo;

import java.math.BigDecimal;

public record BudgetReadinessVo(
        boolean enabled,
        BigDecimal maxJobCostUsd,
        boolean dailyBudgetGuardEnabled,
        BigDecimal maxDailyCostUsd,
        String budgetIdentity,
        boolean estimatedCostTrackingEnabled
) {
}
