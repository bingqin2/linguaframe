package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;

import java.time.Instant;
import java.util.List;

public record SubtitleDraftSegmentRecord(
        String id,
        String jobId,
        String language,
        int segmentIndex,
        String text,
        SubtitleReviewDecision reviewDecision,
        List<SubtitleReviewIssueCategory> issueCategories,
        String reviewerNote,
        Instant createdAt,
        Instant updatedAt
) {
    public SubtitleDraftSegmentRecord(
            String id,
            String jobId,
            String language,
            int segmentIndex,
            String text,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(id, jobId, language, segmentIndex, text, SubtitleReviewDecision.EDITED, List.of(), null, createdAt, updatedAt);
    }
}
