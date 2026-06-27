package com.linguaframe.job.domain.vo;

public record JobCacheSummaryVo(
        int cacheHitCount,
        int generatedArtifactCount,
        int providerCacheHitCount
) {
}
