package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DemoPresenterPackRunVo(
        String jobId,
        String demoProfileId,
        LocalizationJobStatus status,
        Instant completedAt,
        Integer qualityScore,
        BigDecimal estimatedCostUsd,
        int modelCallCount,
        int providerCacheHitCount,
        boolean handoffReady,
        List<String> roles
) {
}
