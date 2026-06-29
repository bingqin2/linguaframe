package com.linguaframe.job.domain.vo;

public record DemoReviewerWorkspaceCheckVo(
        String key,
        String label,
        String status,
        String detail,
        String nextAction,
        boolean required
) {
}
