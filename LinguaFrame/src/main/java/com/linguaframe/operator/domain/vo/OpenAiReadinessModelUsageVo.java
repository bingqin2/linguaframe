package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;

public record OpenAiReadinessModelUsageVo(
        String ledgerStatus,
        int modelCallCount,
        int failedModelCallCount,
        BigDecimal failureRatePercent,
        BigDecimal estimatedCostUsd,
        String recommendedNextAction
) {
}
