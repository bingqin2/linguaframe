package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record TtsCacheEntryRecord(
        String id,
        String cacheKey,
        String textHash,
        String language,
        String provider,
        String model,
        String voice,
        String responseJson,
        String sourceJobId,
        Instant createdAt
) {
}
