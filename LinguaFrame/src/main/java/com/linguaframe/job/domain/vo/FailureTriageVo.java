package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.FailureTriageCategory;

import java.util.List;

public record FailureTriageVo(
        FailureTriageCategory category,
        String summary,
        String recommendedAction,
        boolean retryable,
        String runbookCommand,
        List<String> safeDetails
) {
}
