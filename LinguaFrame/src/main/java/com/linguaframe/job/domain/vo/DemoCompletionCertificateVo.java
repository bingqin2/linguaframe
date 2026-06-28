package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;
import java.util.List;

public record DemoCompletionCertificateVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String certificateStatus,
        LocalizationJobStatus jobStatus,
        String targetLanguage,
        String demoProfileId,
        String headline,
        String summary,
        String recommendedNextAction,
        String recommendedBaselineJobId,
        String bestQualityJobId,
        String lowestCostJobId,
        List<DemoCompletionCertificateCheckVo> checks,
        List<DemoCompletionCertificateSectionVo> sections,
        List<DemoCompletionCertificateLinkVo> links,
        List<String> safetyNotes
) {
}
