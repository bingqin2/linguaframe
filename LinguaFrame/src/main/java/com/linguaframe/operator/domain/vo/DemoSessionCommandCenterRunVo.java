package com.linguaframe.operator.domain.vo;

public record DemoSessionCommandCenterRunVo(
        String role,
        String jobId,
        String videoId,
        String profileId,
        String status,
        String readiness,
        String acceptanceStatus,
        String currentStage,
        Long elapsedMs,
        String nextAction
) {
}
