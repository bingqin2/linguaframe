package com.linguaframe.job.domain.entity;

import com.linguaframe.job.domain.enums.QualityEvaluationStatus;

import java.time.Instant;
import java.util.List;

public record QualityEvaluationRecord(
        String id,
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
