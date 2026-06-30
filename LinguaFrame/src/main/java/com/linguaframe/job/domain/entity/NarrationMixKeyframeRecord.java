package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.NarrationMixLane;

import java.math.BigDecimal;
import java.time.Instant;

public record NarrationMixKeyframeRecord(
        String id,
        String jobId,
        NarrationMixLane lane,
        BigDecimal timeSeconds,
        BigDecimal value,
        Instant createdAt,
        Instant updatedAt
) {
}
