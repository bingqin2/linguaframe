package com.linguaframe.media.domain.vo;

import java.math.BigDecimal;

public record UploadExecutionPlanStageVo(
        String id,
        String label,
        String status,
        String executionType,
        String provider,
        String model,
        boolean runnable,
        BigDecimal estimatedCostUsd,
        Integer estimatedDurationSecondsLower,
        Integer estimatedDurationSecondsUpper,
        String detail
) {
}
