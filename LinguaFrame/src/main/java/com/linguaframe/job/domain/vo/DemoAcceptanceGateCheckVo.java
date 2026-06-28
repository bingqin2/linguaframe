package com.linguaframe.job.domain.vo;

public record DemoAcceptanceGateCheckVo(
        String key,
        String label,
        String status,
        String detail,
        boolean required
) {
}
