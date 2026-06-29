package com.linguaframe.operator.domain.vo;

public record OpenAiReadinessLiveCheckVo(
        String status,
        long latencyMs,
        String message
) {
}
