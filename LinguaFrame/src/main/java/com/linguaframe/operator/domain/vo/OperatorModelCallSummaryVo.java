package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;

public record OperatorModelCallSummaryVo(
        long modelCallCount,
        long failedModelCallCount,
        long totalLatencyMs,
        BigDecimal estimatedCostUsd
) {
}
