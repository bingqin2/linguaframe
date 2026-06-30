package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationRenderReviewVo(
        String jobId,
        String status,
        String nextAction,
        int segmentCount,
        BigDecimal totalNarrationDurationSeconds,
        BigDecimal coveredSpanSeconds,
        int gapCount,
        BigDecimal gapSeconds,
        boolean timelineHasOverlap,
        String voiceSummary,
        int segmentMixOverrideCount,
        String segmentMixOverrideSummary,
        int mixKeyframeCount,
        String mixKeyframeLaneSummary,
        boolean audioReady,
        int audioArtifactCount,
        boolean videoReady,
        int narratedVideoArtifactCount,
        boolean waveformReady,
        String waveformArtifactId,
        boolean waveformCacheHit,
        List<NarrationRenderReviewMetricVo> metrics,
        List<NarrationRenderReviewCheckVo> checks,
        List<NarrationRenderReviewLinkVo> safeLinks,
        List<String> safetyNotes
) {
}
