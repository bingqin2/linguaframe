package com.linguaframe.job.domain.vo;

public record NarrationRecoveryHandoffStepVo(
        String key,
        String label,
        String status,
        String action,
        String safeCommand,
        String safeLink
) {
}
