package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobArtifactType;

public record DeliveryManifestArtifactVo(
        String artifactId,
        JobArtifactType type,
        String filename,
        String contentType,
        long sizeBytes,
        String shortSha256,
        String cacheState,
        String role,
        String downloadUrl
) {
}
