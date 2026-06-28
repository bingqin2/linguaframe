package com.linguaframe.operator.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;

public record OperatorStageTimingVo(
        LocalizationJobStage stage,
        long completedEventCount,
        long failedEventCount,
        long averageDurationMs,
        long maxDurationMs,
        long latestDurationMs
) {
}
