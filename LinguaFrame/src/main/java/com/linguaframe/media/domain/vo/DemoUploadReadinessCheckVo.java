package com.linguaframe.media.domain.vo;

public record DemoUploadReadinessCheckVo(
        String id,
        String label,
        String status,
        String detail,
        String nextAction,
        boolean blocking
) {
}
