package com.linguaframe.job.domain.bo;

public record CreateTranscriptionCacheEntryCommand(
        String cacheKey,
        String audioHash,
        String provider,
        String model,
        String promptVersion,
        String responseJson,
        String sourceJobId
) {
}
