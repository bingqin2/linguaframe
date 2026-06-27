package com.linguaframe.job.domain.bo;

public record TranslationCacheLookupBo(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion
) {
}
