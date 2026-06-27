package com.linguaframe.operator.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

public record OperatorJobStatusCountVo(
        LocalizationJobStatus status,
        long count
) {
}
