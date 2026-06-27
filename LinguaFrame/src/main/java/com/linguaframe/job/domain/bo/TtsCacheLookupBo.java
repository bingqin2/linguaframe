package com.linguaframe.job.domain.bo;

public record TtsCacheLookupBo(
        String cacheKey,
        String textHash,
        String language,
        String provider,
        String model,
        String voice
) {
}
