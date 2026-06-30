package com.linguaframe.job.domain.vo;

public record NarrationDeliveryPackageCheckVo(
        String key,
        String label,
        String status,
        String detail,
        String nextAction,
        boolean required
) {
}
