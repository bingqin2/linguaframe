package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record TranscriptionCacheEntryRecord(
        String id,
        String cacheKey,
        String audioHash,
        String provider,
        String model,
        String promptVersion,
        String responseJson,
        String sourceJobId,
        Instant createdAt
) {
}
