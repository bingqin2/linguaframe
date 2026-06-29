package com.linguaframe.job.domain.entity;

import java.math.BigDecimal;
import java.time.Instant;

public record NarrationSegmentRecord(
        String id,
        String jobId,
        int segmentIndex,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        String text,
        String voice,
        Instant createdAt,
        Instant updatedAt
) {
}
