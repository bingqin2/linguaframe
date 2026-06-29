package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record OpenAiSmokeProofCallVo(
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
        String safeErrorSummary
) {
}
