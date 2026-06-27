package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobArtifactType;

import java.time.Instant;

public record JobDiagnosticsArtifactVo(
        String artifactId,
        JobArtifactType type,
        String filename,
        String contentType,
        long sizeBytes,
        String contentSha256,
        boolean cacheHit,
        String sourceArtifactId,
        Instant createdAt
) {
}
