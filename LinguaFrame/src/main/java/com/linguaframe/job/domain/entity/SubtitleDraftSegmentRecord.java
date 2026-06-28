package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record SubtitleDraftSegmentRecord(
        String id,
        String jobId,
        String language,
        int segmentIndex,
        String text,
        Instant createdAt,
        Instant updatedAt
) {
}
