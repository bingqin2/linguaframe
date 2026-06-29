package com.linguaframe.demo.domain.vo;

import java.math.BigDecimal;

public record NarrationDemoPresetMixSettingsVo(
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        int fadeDurationMs
) {
}
