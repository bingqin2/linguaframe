package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record ReviewedSubtitleWorkflowVo(
        String jobId,
        String videoId,
        String targetLanguage,
        Instant generatedAt,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        int segmentCount,
        int missingTargetCount,
        int timingMismatchCount,
        Integer qualityScore,
        String qualityVerdict,
        int qualityIssueCount,
        int qualitySuggestedFixCount,
        int editedSegmentCount,
        Instant draftLastUpdatedAt,
        int generatedSubtitleArtifactCount,
        int reviewedSubtitleArtifactCount,
        boolean reviewedBurnedVideoAvailable,
        boolean handoffReady,
        List<ReviewedSubtitleWorkflowCheckVo> checks,
        List<ReviewedSubtitleWorkflowLinkVo> links,
        List<String> safetyNotes
) {
}
