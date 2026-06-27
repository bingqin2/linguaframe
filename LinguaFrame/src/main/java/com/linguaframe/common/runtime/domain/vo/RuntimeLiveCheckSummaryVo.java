package com.linguaframe.common.runtime.domain.vo;

import java.time.Instant;
import java.util.Map;

public record RuntimeLiveCheckSummaryVo(
        boolean healthy,
        Instant checkedAt,
        Map<String, RuntimeProbeResultVo> checks
) {
}
