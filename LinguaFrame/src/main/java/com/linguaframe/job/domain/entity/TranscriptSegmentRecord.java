package com.linguaframe.job.domain.entity;

import java.time.Instant;

public record TranscriptSegmentRecord(
        String id,
        String jobId,
        int segmentIndex,
        long startMs,
        long endMs,
        String text,
        Instant createdAt
) {
}
