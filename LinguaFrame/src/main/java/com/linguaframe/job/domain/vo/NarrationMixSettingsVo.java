package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record NarrationMixSettingsVo(
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        int fadeDurationMs,
        Instant updatedAt
) {
}
