package com.linguaframe.common.runtime.domain.vo;

import com.linguaframe.job.domain.enums.WorkerRole;

public record WorkerReadinessVo(
        boolean dispatchEnabled,
        boolean executionEnabled,
        WorkerRole role,
        int maxRetries,
        int dispatchBatchSize,
        long dispatchIntervalMs
) {
}
