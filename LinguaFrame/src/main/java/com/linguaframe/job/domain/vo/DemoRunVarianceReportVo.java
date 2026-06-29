package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoRunVarianceReportVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String overallStatus,
        String baselineMode,
        String jobStatus,
        String targetLanguage,
        String demoProfileId,
        String recommendedNextAction,
        List<DemoRunVarianceMetricVo> metrics,
        List<String> notes,
        List<String> safeLinks,
        List<String> safetyNotes
) {
}
