package com.linguaframe.common.runtime.domain.vo;

import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;

public record RuntimeProbeResultVo(
        RuntimeProbeStatus status,
        long latencyMs,
        String message
) {
}
