package com.linguaframe.job.domain.vo;

public record NarratedVideoGenerationVo(
        String jobId,
        String artifactId,
        String filename,
        String contentType,
        long sizeBytes,
        String baseVideoType,
        String narrationAudioArtifactId,
        String status
) {
}
