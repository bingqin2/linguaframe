package com.linguaframe.job.domain.bo;

public record TranslationCacheLookupBo(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String translationStyle,
        String translationGlossaryHash
) {
    public TranslationCacheLookupBo(
            String cacheKey,
            String sourceHash,
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            String translationStyle
    ) {
        this(cacheKey, sourceHash, targetLanguage, provider, model, promptVersion, translationStyle, "");
    }

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
