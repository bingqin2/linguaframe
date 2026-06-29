package com.linguaframe.media.domain.vo;

public record UploadExecutionPlanGateVo(
        String id,
        String label,
        String status,
        boolean blocking,
        String detail,
        String nextAction
) {
}
