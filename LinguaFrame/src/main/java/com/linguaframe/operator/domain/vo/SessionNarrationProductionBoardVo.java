package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record SessionNarrationProductionBoardVo(
        Instant generatedAt,
        String overallStatus,
        String headline,
        String recommendedNextAction,
        int limit,
        int readyToDeliverCount,
        int needsReviewCount,
        int needsRenderCount,
        int needsAuthoringCount,
        int blockedCount,
        int notApplicableCount,
        SessionNarrationProductionActionVo primaryAction,
        List<SessionNarrationProductionJobVo> jobs,
        List<SessionNarrationProductionCheckVo> checks,
        List<SessionNarrationProductionLinkVo> links,
        List<String> safetyNotes,
        String markdown
) {
}
