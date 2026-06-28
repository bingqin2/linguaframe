package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.SubtitleReviewSegmentStatus;

public record SubtitleReviewSegmentVo(
        int index,
        long startMs,
        long endMs,
        String sourceText,
        String targetText,
        long durationMs,
        long timingDeltaMs,
        SubtitleReviewSegmentStatus status
) {
}
