package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record NarrationTimelineSegmentVo(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal durationSeconds,
        BigDecimal leftPercent,
        BigDecimal widthPercent,
        String status,
        int characterCount,
        String voice
) {
}
