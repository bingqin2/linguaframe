package com.linguaframe.job.domain.bo;

public record TranslationCacheLookupBo(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String translationStyle
) {
    public TranslationCacheLookupBo(
            String cacheKey,
            String sourceHash,
            String targetLanguage,
            String provider,
            String model,
            String promptVersion
    ) {
        this(cacheKey, sourceHash, targetLanguage, provider, model, promptVersion, "NATURAL");
    }
}
