package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ModelCallVo(
        String modelCallId,
        String jobId,
        LocalizationJobStage stage,
        ModelCallOperation operation,
        ModelCallProvider provider,
        String model,
        String promptVersion,
        ModelCallStatus status,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal audioSeconds,
        Integer characterCount,
        String inputSummary,
        String outputSummary,
        BigDecimal estimatedCostUsd,
        String safeErrorSummary,
        Instant createdAt
) {
}
