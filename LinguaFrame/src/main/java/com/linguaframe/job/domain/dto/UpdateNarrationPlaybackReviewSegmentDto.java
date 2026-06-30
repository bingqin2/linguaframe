package com.linguaframe.job.domain.dto;

import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;

import java.util.List;

public record UpdateNarrationPlaybackReviewSegmentDto(
        NarrationPlaybackReviewDecision decision,
        List<NarrationPlaybackIssueCategory> issueCategories,
        String reviewerNote
) {
}
