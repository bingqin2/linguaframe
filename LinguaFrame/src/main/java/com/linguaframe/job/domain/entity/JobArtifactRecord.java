package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.JobArtifactType;

import java.time.Instant;

public record JobArtifactRecord(
        String id,
        String jobId,
        JobArtifactType type,
        String objectKey,
        String filename,
        String contentType,
        long sizeBytes,
        String contentSha256,
        boolean cacheHit,
        String sourceArtifactId,
        Instant createdAt
) {
}
