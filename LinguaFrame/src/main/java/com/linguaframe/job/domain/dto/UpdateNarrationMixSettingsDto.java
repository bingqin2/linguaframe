package com.linguaframe.job.domain.dto;

import java.math.BigDecimal;

public record UpdateNarrationMixSettingsDto(
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        Integer fadeDurationMs
) {
}
