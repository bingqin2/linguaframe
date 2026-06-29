package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record SubtitleDraftSummaryVo(
        String jobId,
        String targetLanguage,
        int segmentCount,
        int editedSegmentCount,
        int reviewedSegmentCount,
        int acceptedSegmentCount,
        int editedDecisionCount,
        int followupSegmentCount,
        int annotationCount,
        int reviewerNoteCount,
        Instant lastUpdatedAt,
        List<SubtitleDraftSegmentVo> segments
) {
    public SubtitleDraftSummaryVo(
            String jobId,
            String targetLanguage,
            int segmentCount,
            int editedSegmentCount,
            Instant lastUpdatedAt,
            List<SubtitleDraftSegmentVo> segments
    ) {
        this(
                jobId,
                targetLanguage,
                segmentCount,
                editedSegmentCount,
                editedSegmentCount,
                0,
                editedSegmentCount,
                0,
                0,
                0,
                lastUpdatedAt,
                segments
        );
    }
}
