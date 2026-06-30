package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NarrationWaveformVo(
        String jobId,
        String status,
        String sourceType,
        int bucketCount,
        BigDecimal durationSeconds,
        List<NarrationWaveformBucketVo> buckets,
        String fallbackReason,
        String artifactId,
        String sourceArtifactId,
        String sourceContentSha256,
        boolean cacheHit,
        String contentSha256,
        Instant generatedAt
) {
}
