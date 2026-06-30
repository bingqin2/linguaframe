package com.linguaframe.job.domain.vo;

public record StuckJobRecoveryLinkVo(
        String kind,
        String label,
        String href,
        String contentType,
        String description
) {
}
