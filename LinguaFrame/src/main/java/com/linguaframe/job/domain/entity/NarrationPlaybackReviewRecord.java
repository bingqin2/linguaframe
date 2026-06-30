package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;

import java.time.Instant;
import java.util.List;

public record NarrationPlaybackReviewRecord(
        String id,
        String jobId,
        int segmentIndex,
        NarrationPlaybackReviewDecision decision,
        List<NarrationPlaybackIssueCategory> issueCategories,
        String reviewerNote,
        Instant createdAt,
        Instant updatedAt
) {
}
