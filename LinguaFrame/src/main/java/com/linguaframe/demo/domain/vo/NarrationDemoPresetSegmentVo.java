package com.linguaframe.demo.domain.vo;

import java.math.BigDecimal;

public record NarrationDemoPresetSegmentVo(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal durationSeconds,
        String text,
        int characterCount,
        String voice
) {
}
