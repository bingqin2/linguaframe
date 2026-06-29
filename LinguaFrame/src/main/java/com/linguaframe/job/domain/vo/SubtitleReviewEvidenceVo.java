package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record SubtitleReviewEvidenceVo(
        String jobId,
        String videoId,
        String targetLanguage,
        Instant generatedAt,
        String status,
        String summary,
        int segmentCount,
        int reviewedSegmentCount,
        int acceptedSegmentCount,
        int editedDecisionCount,
        int followupSegmentCount,
        int annotationCount,
        int reviewerNoteCount,
        int reviewedSubtitleArtifactCount,
        boolean reviewedBurnedVideoAvailable,
        int releaseNotesLength,
        List<SubtitleReviewEvidenceCategoryVo> decisionCounts,
        List<SubtitleReviewEvidenceCategoryVo> issueCategoryCounts,
        List<SubtitleReviewEvidenceCheckVo> checks,
        List<ReviewedSubtitleWorkflowLinkVo> links,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
