package com.linguaframe.job.domain.dto;

import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;

import java.util.List;

public record UpdateSubtitleDraftRequest(
        List<Segment> segments
) {

    public record Segment(
            int index,
            String text,
            SubtitleReviewDecision decision,
            List<SubtitleReviewIssueCategory> issueCategories,
            String reviewerNote
    ) {
        public Segment(int index, String text) {
            this(index, text, SubtitleReviewDecision.EDITED, List.of(), null);
        }
    }
}
