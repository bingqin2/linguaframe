package com.linguaframe.job.domain.bo;

public record CreateTtsCacheEntryCommand(
        String cacheKey,
        String textHash,
        String language,
        String provider,
        String model,
        String voice,
        String responseJson,
        String sourceJobId
) {
}
