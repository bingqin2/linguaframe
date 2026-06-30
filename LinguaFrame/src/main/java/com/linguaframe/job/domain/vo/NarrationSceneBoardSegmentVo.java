package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record NarrationSceneBoardSegmentVo(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal durationSeconds,
        String windowLabel,
        String voiceState,
        int characterCount,
        BigDecimal readingDensity,
        String timingStatus,
        String mixState,
        String readiness
) {
}
