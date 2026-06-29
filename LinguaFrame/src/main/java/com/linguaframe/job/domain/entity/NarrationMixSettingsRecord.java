package com.linguaframe.job.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record NarrationMixSettingsRecord(
        String jobId,
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        int fadeDurationMs,
        Instant updatedAt
) {
}
