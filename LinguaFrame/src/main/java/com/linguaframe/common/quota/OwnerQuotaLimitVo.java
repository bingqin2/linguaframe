package com.linguaframe.common.quota;

import java.math.BigDecimal;

public record OwnerQuotaLimitVo(
        String name,
        boolean enabled,
        BigDecimal limit,
        BigDecimal current
) {
}
