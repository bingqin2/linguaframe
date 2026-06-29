package com.linguaframe.media.domain.vo;

public record UploadExecutionPlanCommandVo(
        String id,
        String label,
        String command,
        String description
) {
}
