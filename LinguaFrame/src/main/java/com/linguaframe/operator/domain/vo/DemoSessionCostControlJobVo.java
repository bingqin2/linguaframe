package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DemoSessionCostControlJobVo(
        String jobId,
        String videoId,
        String jobStatus,
        String targetLanguage,
        String demoProfileId,
        int modelCallCount,
        int failedModelCallCount,
        BigDecimal estimatedCostUsd,
        Instant latestModelCallAt,
        List<String> safeLinks
) {
}
