package com.linguaframe.operator.domain.vo;

public record OpenAiReadinessProviderVo(
        String stage,
        boolean enabled,
        String provider,
        String model,
        boolean credentialsConfigured,
        String status,
        String detail,
        boolean paidProvider
) {
}
