package com.linguaframe.job.domain.dto;

import com.linguaframe.job.domain.enums.NarrationMixLane;

import java.math.BigDecimal;

public record SaveNarrationMixKeyframeDto(
        NarrationMixLane lane,
        BigDecimal timeSeconds,
        BigDecimal value
) {
}
