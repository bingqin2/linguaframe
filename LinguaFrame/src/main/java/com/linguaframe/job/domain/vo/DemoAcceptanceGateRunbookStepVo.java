package com.linguaframe.job.domain.vo;

public record DemoAcceptanceGateRunbookStepVo(
        String key,
        String label,
        String status,
        String detail,
        String primaryAction,
        String safeCommand,
        String safeLink
) {
}
