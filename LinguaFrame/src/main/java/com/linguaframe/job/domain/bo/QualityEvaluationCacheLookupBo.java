package com.linguaframe.job.domain.bo;

public record QualityEvaluationCacheLookupBo(
        String cacheKey,
        String sourceHash,
        String targetHash,
        String language,
        String provider,
        String model,
        String promptVersion
) {
}
