package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NarrationPlaybackReviewResolutionSegmentVo(
        int segmentIndex,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal durationSeconds,
        String decision,
        String resolutionStatus,
        List<String> issueCategories,
        String nextAction,
        boolean reviewerNotePresent,
        Instant reviewedAt
) {
}
