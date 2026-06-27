package com.linguaframe.job.domain.bo;

public record CreateQualityEvaluationCacheEntryCommand(
        String cacheKey,
        String sourceHash,
        String targetHash,
        String language,
        String provider,
        String model,
        String promptVersion,
        String responseJson,
        String sourceJobId
) {
}
