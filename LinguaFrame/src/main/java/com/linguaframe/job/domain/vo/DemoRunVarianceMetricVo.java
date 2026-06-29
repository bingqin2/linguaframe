package com.linguaframe.job.domain.vo;

public record DemoRunVarianceMetricVo(
        String id,
        String label,
        String status,
        String estimatedValue,
        String actualValue,
        String detail
) {
}
