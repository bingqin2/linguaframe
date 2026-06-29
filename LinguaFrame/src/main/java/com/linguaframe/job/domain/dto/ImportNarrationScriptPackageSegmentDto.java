package com.linguaframe.job.domain.dto;

import java.math.BigDecimal;

public record ImportNarrationScriptPackageSegmentDto(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String text,
        String voice
) {
}
