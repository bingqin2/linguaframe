package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SessionNarrationProductionJobVo(
        String jobId,
        String videoId,
        String jobStatus,
        String classification,
        String attentionLevel,
        String targetLanguage,
        Instant createdAt,
        Instant completedAt,
        int segmentCount,
        BigDecimal coveragePercent,
        int gapCount,
        boolean hasOverlap,
        int voiceCount,
        int mixKeyframeCount,
        boolean sceneBoardReady,
        boolean audioReady,
        boolean videoReady,
        boolean renderReviewReady,
        boolean playbackResolved,
        boolean deliveryReady,
        boolean acceptanceReady,
        String primaryBlocker,
        String recommendedNextAction,
        List<SessionNarrationProductionCheckVo> checks,
        List<SessionNarrationProductionActionVo> actions,
        List<SessionNarrationProductionLinkVo> links
) {
}
