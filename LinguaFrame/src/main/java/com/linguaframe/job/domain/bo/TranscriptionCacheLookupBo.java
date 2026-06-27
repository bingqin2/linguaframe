package com.linguaframe.job.domain.bo;

public record TranscriptionCacheLookupBo(
        String cacheKey,
        String audioHash,
        String provider,
        String model,
        String promptVersion
) {
}
