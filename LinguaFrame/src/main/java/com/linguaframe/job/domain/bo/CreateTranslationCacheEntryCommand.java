package com.linguaframe.job.domain.bo;

public record CreateTranslationCacheEntryCommand(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String translationGlossaryHash,
        String responseJson,
        String sourceJobId
) {
    public CreateTranslationCacheEntryCommand(
            String cacheKey,
            String sourceHash,
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            String responseJson,
            String sourceJobId
    ) {
        this(cacheKey, sourceHash, targetLanguage, provider, model, promptVersion, "", responseJson, sourceJobId);
    }
}
