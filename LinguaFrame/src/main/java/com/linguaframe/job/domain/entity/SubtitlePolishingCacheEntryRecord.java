package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record SubtitlePolishingCacheEntryRecord(
        String id,
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String subtitlePolishingMode,
        String responseJson,
        String sourceJobId,
        Instant createdAt
) {
}
