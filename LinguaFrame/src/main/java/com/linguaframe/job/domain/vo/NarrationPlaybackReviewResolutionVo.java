package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record NarrationPlaybackReviewResolutionVo(
        String jobId,
        Instant generatedAt,
        String status,
        String nextAction,
        int segmentCount,
        int readySegmentCount,
        int unresolvedSegmentCount,
        int textRevisionRequiredCount,
        int rerenderRequiredCount,
        int unreviewedSegmentCount,
        boolean audioReady,
        int audioArtifactCount,
        boolean videoReady,
        int narratedVideoArtifactCount,
        List<NarrationPlaybackReviewResolutionSegmentVo> unresolvedSegments,
        List<NarrationPlaybackReviewResolutionLinkVo> safeLinks,
        List<String> safetyNotes
) {
}
