package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ModelUsageLedgerJobVo(
        String jobId,
        String videoId,
        String jobStatus,
        String targetLanguage,
        String demoProfileId,
        int modelCallCount,
        int failedModelCallCount,
        int providerCacheHitCount,
        int generatedArtifactCount,
        long totalLatencyMs,
        BigDecimal estimatedCostUsd,
        Instant latestModelCallAt,
        List<String> safeLinks
) {
}
