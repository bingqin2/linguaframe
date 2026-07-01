package com.linguaframe.operator.domain.vo;

public record DemoSessionCostControlCheckVo(
        String key,
        String label,
        String status,
        String detail,
        String nextAction,
        boolean blocking
) {
}
