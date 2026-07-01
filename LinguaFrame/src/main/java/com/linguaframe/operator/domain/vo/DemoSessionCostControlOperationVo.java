package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;

public record DemoSessionCostControlOperationVo(
        String operation,
        String provider,
        String model,
        String promptVersion,
        int modelCallCount,
        int failedModelCallCount,
        BigDecimal estimatedCostUsd,
        long averageLatencyMs
) {
}
