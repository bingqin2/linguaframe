package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NarrationSceneBoardVo(
        String jobId,
        Instant generatedAt,
        String status,
        int segmentCount,
        BigDecimal totalNarrationSeconds,
        BigDecimal totalSpanSeconds,
        BigDecimal coveragePercent,
        int gapCount,
        BigDecimal gapSeconds,
        boolean hasOverlap,
        int voiceCount,
        int mixOverrideCount,
        int mixKeyframeCount,
        boolean audioReady,
        boolean videoReady,
        List<NarrationSceneBoardSegmentVo> segments,
        List<NarrationSceneBoardCheckVo> checks,
        List<NarrationSceneBoardActionVo> recommendedActions,
        List<NarrationSceneBoardLinkVo> safeLinks,
        List<String> safetyNotes
) {
}
