package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record QualityEvaluationCacheEntryRecord(
        String id,
        String cacheKey,
        String sourceHash,
        String targetHash,
        String language,
        String provider,
        String model,
        String promptVersion,
        String responseJson,
        String sourceJobId,
        Instant createdAt
) {
}
