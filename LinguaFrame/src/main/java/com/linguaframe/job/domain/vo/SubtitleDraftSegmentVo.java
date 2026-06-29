package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;

import java.time.Instant;
import java.util.List;

public record SubtitleDraftSegmentVo(
        int index,
        long startMs,
        long endMs,
        String sourceText,
        String generatedText,
        String draftText,
        boolean edited,
        Instant updatedAt,
        SubtitleReviewDecision decision,
        List<SubtitleReviewIssueCategory> issueCategories,
        String reviewerNote,
        int noteLength
) {
    public SubtitleDraftSegmentVo(
            int index,
            long startMs,
            long endMs,
            String sourceText,
            String generatedText,
            String draftText,
            boolean edited,
            Instant updatedAt
    ) {
        this(
                index,
                startMs,
                endMs,
                sourceText,
                generatedText,
                draftText,
                edited,
                updatedAt,
                edited ? SubtitleReviewDecision.EDITED : SubtitleReviewDecision.UNREVIEWED,
                List.of(),
                null,
                0
        );
    }
}
