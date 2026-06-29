package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record ModelUsageLedgerCallVo(
        String modelCallId,
        String jobId,
        String videoId,
        String stage,
        String operation,
        String provider,
        String model,
        String promptVersion,
        String status,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal audioSeconds,
        Integer characterCount,
        BigDecimal estimatedCostUsd,
        String safeErrorSummary,
        Instant createdAt
) {
}
