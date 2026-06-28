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
        String translationGlossaryHash,
        String responseJson,
        String sourceJobId,
        Instant createdAt
) {
    public TranslationCacheEntryRecord(
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
        this(id, cacheKey, sourceHash, targetLanguage, provider, model, promptVersion, "", responseJson, sourceJobId, createdAt);
    }
}
