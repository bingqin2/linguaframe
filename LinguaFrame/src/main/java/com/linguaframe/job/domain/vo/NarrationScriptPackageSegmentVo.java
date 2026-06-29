package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;

public record NarrationScriptPackageSegmentVo(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal durationSeconds,
        String text,
        String voice,
        int characterCount,
        Instant updatedAt
) {
}
