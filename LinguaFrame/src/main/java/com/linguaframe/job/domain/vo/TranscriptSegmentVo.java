package com.linguaframe.job.domain.vo;

public record TranscriptSegmentVo(
        int index,
        long startMs,
        long endMs,
        String text
) {
}
