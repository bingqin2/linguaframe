package com.linguaframe.media.domain.vo;

import java.math.BigDecimal;

public record UploadCostEstimateStageVo(
        String id,
        String label,
        String status,
        String provider,
        String model,
        boolean paidProviderCall,
        BigDecimal estimatedCostUsd,
        String basis,
        String detail
) {
}
