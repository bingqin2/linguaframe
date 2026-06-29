package com.linguaframe.media.domain.vo;

import java.math.BigDecimal;

public record UploadCostEstimateBudgetVo(
        String id,
        String label,
        boolean enabled,
        String status,
        BigDecimal currentUsd,
        BigDecimal estimateUsd,
        BigDecimal projectedUsd,
        BigDecimal limitUsd,
        String detail
) {
}
