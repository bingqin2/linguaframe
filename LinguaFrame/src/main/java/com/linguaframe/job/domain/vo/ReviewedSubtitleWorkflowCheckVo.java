package com.linguaframe.job.domain.vo;

public record ReviewedSubtitleWorkflowCheckVo(
        String key,
        String label,
        String status,
        String detail,
        String nextAction,
        boolean blocking
) {
}
