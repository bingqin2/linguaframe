package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

public record LocalizationJobExecutionResultVo(
        String jobId,
        boolean executed,
        LocalizationJobStatus status
) {
}
