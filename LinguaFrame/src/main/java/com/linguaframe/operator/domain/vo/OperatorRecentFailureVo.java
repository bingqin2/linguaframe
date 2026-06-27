package com.linguaframe.operator.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.time.Instant;

public record OperatorRecentFailureVo(
        String jobId,
        String videoId,
        String filename,
        LocalizationJobStage failureStage,
        String failureReason,
        Instant failedAt
) {
}
