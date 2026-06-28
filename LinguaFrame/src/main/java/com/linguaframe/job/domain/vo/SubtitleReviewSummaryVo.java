package com.linguaframe.job.domain.vo;

import java.util.List;

public record SubtitleReviewSummaryVo(
        String jobId,
        String targetLanguage,
        int segmentCount,
        int missingTargetCount,
        int timingMismatchCount,
        long averageDurationMs,
        long maxDurationMs,
        Integer qualityScore,
        String qualityVerdict,
        int qualityIssueCount,
        int qualitySuggestedFixCount,
        int downloadableSubtitleArtifactCount,
        List<SubtitleReviewSegmentVo> segments
) {
}
