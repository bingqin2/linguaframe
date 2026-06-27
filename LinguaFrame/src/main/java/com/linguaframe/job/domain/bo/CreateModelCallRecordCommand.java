package com.linguaframe.job.domain.bo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;

import java.math.BigDecimal;

public record CreateModelCallRecordCommand(
        String jobId,
        LocalizationJobStage stage,
        ModelCallOperation operation,
        ModelCallProvider provider,
        String model,
        String promptVersion,
        long latencyMs,
        Integer inputTokens,
        Integer outputTokens,
        BigDecimal audioSeconds,
        Integer characterCount,
        String inputSummary,
        String outputSummary
) {
}
