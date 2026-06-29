package com.linguaframe.job.domain.vo;

import java.time.Instant;

public record OpenAiSmokeProofArtifactVo(
        String artifactId,
        String type,
        String filename,
        String contentType,
        long sizeBytes,
        String contentSha256,
        boolean cacheHit,
        Instant createdAt
) {
}
