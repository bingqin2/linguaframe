package com.linguaframe.job.domain.bo;

import java.util.List;

public record QualityEvaluationResultBo(
        int score,
        String verdict,
        int completeness,
        int readability,
        int timingPreservation,
        int naturalness,
        List<String> issues,
        List<String> suggestedFixes
) {
}
