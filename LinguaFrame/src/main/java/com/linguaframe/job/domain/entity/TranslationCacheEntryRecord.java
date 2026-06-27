package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record TranslationCacheEntryRecord(
        String id,
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String responseJson,
        String sourceJobId,
        Instant createdAt
) {
}
