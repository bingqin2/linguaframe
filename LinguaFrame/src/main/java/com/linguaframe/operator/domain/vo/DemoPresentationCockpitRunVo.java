package com.linguaframe.operator.domain.vo;

public record DemoPresentationCockpitRunVo(
        String jobId,
        String videoId,
        String profileId,
        String status,
        String readiness,
        String acceptanceStatus,
        String attentionLevel,
        String currentStage,
        Long elapsedMs,
        String nextAction
) {
}
