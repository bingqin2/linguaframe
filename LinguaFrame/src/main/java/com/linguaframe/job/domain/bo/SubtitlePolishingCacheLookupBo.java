package com.linguaframe.job.domain.bo;

public record SubtitlePolishingCacheLookupBo(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String subtitlePolishingMode
) {
}
