package com.linguaframe.operator.domain.vo;

public record DemoSessionCommandCenterActionVo(
        String id,
        String label,
        String command,
        String description,
        boolean primary
) {
}
