package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.QualityEvaluationStatus;

import java.time.Instant;
import java.util.List;

public record QualityEvaluationVo(
        String evaluationId,
        String jobId,
        String language,
        int score,
        String verdict,
        int completeness,
        int readability,
        int timingPreservation,
        int naturalness,
        List<String> issues,
        List<String> suggestedFixes,
        QualityEvaluationStatus status,
        String safeErrorSummary,
        Instant createdAt
) {
}
