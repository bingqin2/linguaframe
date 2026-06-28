package com.linguaframe.job.domain.vo;

public record DemoCompletionCertificateCheckVo(
        String key,
        String label,
        String status,
        String detail,
        boolean blocking
) {
}
