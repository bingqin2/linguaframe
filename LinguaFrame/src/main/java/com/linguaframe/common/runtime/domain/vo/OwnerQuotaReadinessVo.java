package com.linguaframe.common.runtime.domain.vo;

import java.math.BigDecimal;

public record OwnerQuotaReadinessVo(
        boolean enabled,
        int maxActiveJobs,
        int maxQueuedJobs,
        boolean dailyBudgetGuardEnabled,
        BigDecimal maxDailyCostUsd
) {
}
