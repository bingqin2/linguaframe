package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.NarrationMixLane;

import java.math.BigDecimal;
import java.time.Instant;

public record NarrationMixKeyframeVo(
        NarrationMixLane lane,
        BigDecimal timeSeconds,
        BigDecimal value,
        Instant updatedAt
) {
}
