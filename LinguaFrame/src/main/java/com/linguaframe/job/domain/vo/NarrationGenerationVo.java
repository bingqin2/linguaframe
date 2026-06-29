package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record NarrationGenerationVo(
        String jobId,
        String artifactId,
        String filename,
        String contentType,
        long sizeBytes,
        int segmentCount,
        int totalCharacterCount,
        BigDecimal totalTimelineDurationSeconds,
        String voiceSummary,
        String status
) {
}
