package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record NarrationPlaybackReviewVo(
        String jobId,
        Instant generatedAt,
        String status,
        String nextAction,
        int segmentCount,
        int reviewedSegmentCount,
        int acceptedSegmentCount,
        int needsEditCount,
        int needsRerenderCount,
        int unreviewedSegmentCount,
        boolean audioReady,
        int audioArtifactCount,
        boolean videoReady,
        int narratedVideoArtifactCount,
        List<NarrationPlaybackReviewCategoryCountVo> decisionCounts,
        List<NarrationPlaybackReviewCategoryCountVo> issueCategoryCounts,
        List<NarrationPlaybackReviewSegmentVo> segments,
        List<NarrationPlaybackReviewLinkVo> safeLinks,
        List<String> safetyNotes
) {
}
