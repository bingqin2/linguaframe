package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoEvidenceClosurePackageVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String closureStatus,
        String baselineMode,
        String jobStatus,
        String targetLanguage,
        String demoProfileId,
        String recommendedNextAction,
        DemoRunVarianceReportVo varianceReport,
        List<DemoEvidenceClosureSectionVo> sections,
        List<String> safeLinks,
        List<String> safetyNotes
) {
}
