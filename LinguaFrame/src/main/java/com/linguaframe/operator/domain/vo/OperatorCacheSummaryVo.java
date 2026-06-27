package com.linguaframe.operator.domain.vo;

public record OperatorCacheSummaryVo(
        long artifactCacheHitCount,
        long generatedArtifactCount,
        long providerCacheHitCount
) {
}
