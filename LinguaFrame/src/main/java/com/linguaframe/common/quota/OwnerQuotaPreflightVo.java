package com.linguaframe.common.quota;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record OwnerQuotaPreflightVo(
        String ownerId,
        boolean enabled,
        boolean allowed,
        int activeJobs,
        int queuedJobs,
        BigDecimal dailyEstimatedCostUsd,
        LocalDate dailyBudgetDate,
        List<OwnerQuotaLimitVo> limits,
        List<String> blockingReasons
) {
}
