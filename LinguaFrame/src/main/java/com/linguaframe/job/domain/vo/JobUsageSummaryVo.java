package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record JobUsageSummaryVo(
        int modelCallCount,
        int failedModelCallCount,
        long totalLatencyMs,
        BigDecimal estimatedCostUsd,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal audioSeconds,
        Integer characterCount
) {
}
