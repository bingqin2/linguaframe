package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;

public record DemoSessionCostControlBudgetVo(
        String key,
        String label,
        String status,
        boolean enabled,
        BigDecimal limitUsd,
        BigDecimal currentUsd,
        String detail,
        boolean blocking
) {
}
