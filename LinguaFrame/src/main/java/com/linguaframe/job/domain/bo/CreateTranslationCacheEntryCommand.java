package com.linguaframe.job.domain.bo;

public record CreateTranslationCacheEntryCommand(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String responseJson,
        String sourceJobId
) {
}
