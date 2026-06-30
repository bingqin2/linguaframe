package com.linguaframe.job.domain.vo;

public record NarrationDeliveryPackageArtifactVo(
        String artifactId,
        String artifactType,
        String filename,
        String contentType,
        long sizeBytes,
        boolean cacheHit,
        String downloadHref
) {
}
