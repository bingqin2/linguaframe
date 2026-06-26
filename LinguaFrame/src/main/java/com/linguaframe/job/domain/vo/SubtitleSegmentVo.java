package com.linguaframe.job.domain.vo;

public record SubtitleSegmentVo(
        String language,
        int index,
        long startMs,
        long endMs,
        String text
) {
}
