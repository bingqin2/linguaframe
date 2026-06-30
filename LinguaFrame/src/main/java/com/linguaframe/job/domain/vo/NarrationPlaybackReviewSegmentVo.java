package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NarrationPlaybackReviewSegmentVo(
        int segmentIndex,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal durationSeconds,
        String decision,
        List<String> issueCategories,
        boolean reviewerNotePresent,
        Instant reviewedAt
) {
}
