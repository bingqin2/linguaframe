package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record SubtitleSegmentRecord(
        String id,
        String jobId,
        String language,
        int segmentIndex,
        long startMs,
        long endMs,
        String text,
        Instant createdAt
) {
}
