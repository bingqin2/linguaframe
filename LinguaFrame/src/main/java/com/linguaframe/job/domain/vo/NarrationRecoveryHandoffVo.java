package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record NarrationRecoveryHandoffVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String status,
        String phase,
        String headline,
        String recommendedNextAction,
        String acceptanceGateStatus,
        String playbackResolutionStatus,
        int unresolvedSegmentCount,
        int textRevisionRequiredCount,
        int rerenderRequiredCount,
        int unreviewedSegmentCount,
        boolean audioReady,
        boolean videoReady,
        List<NarrationRecoveryHandoffCheckVo> checks,
        List<NarrationRecoveryHandoffStepVo> steps,
        List<NarrationRecoveryHandoffLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
