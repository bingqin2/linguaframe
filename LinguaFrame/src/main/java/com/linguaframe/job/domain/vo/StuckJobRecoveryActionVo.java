package com.linguaframe.job.domain.vo;

public record StuckJobRecoveryActionVo(
        String id,
        String label,
        String method,
        String href,
        boolean enabled,
        boolean requiresConfirmation,
        String description
) {
}
