package com.linguaframe.common.runtime.domain.vo;

public record MediaReadinessVo(
        int maxFileSizeMb,
        int maxDurationSeconds
) {
}
