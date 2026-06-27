package com.linguaframe.common.runtime.domain.vo;

public record ProviderReadinessVo(
        boolean enabled,
        String provider,
        String model,
        boolean credentialsConfigured
) {
}
