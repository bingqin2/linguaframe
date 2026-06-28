package com.linguaframe.operator.domain.vo;

public record PrivateDemoLaunchRehearsalStepVo(
        String id,
        String title,
        String status,
        String detail,
        String command,
        String evidencePath,
        String nextAction,
        boolean blocking
) {
}
