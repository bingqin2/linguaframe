package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record NarratedVideoGenerationVo(
        String jobId,
        String artifactId,
        String filename,
        String contentType,
        long sizeBytes,
        String baseVideoType,
        String narrationAudioArtifactId,
        String mixMode,
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        int fadeDurationMs,
        int narrationWindowCount,
        String status
) {
}
