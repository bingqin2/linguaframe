package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;

public record ModelUsageLedgerOperationVo(
        String operation,
        String provider,
        String model,
        String promptVersion,
        int modelCallCount,
        int failedModelCallCount,
        long totalLatencyMs,
        BigDecimal estimatedCostUsd,
        long averageLatencyMs
) {
}
