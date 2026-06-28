package com.linguaframe.job.domain.bo;

public record CreateSubtitlePolishingCacheEntryCommand(
        String cacheKey,
        String sourceHash,
        String targetLanguage,
        String provider,
        String model,
        String promptVersion,
        String subtitlePolishingMode,
        String responseJson,
        String sourceJobId
) {
}
