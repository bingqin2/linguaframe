package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record JobComparisonDeltaVo(
        Integer qualityScore,
        int modelCallCount,
        BigDecimal estimatedCostUsd,
        int artifactCacheHitCount,
        int generatedArtifactCount,
        int providerCacheHitCount
) {
}
