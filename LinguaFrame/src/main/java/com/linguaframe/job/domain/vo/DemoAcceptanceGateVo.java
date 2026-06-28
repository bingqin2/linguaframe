package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;
import java.util.List;

public record DemoAcceptanceGateVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String gateStatus,
        LocalizationJobStatus jobStatus,
        String targetLanguage,
        String demoProfileId,
        String headline,
        String summary,
        String recommendedNextAction,
        List<DemoAcceptanceGateCheckVo> checks,
        List<DemoAcceptanceGateEvidenceVo> evidence,
        List<DemoAcceptanceGateLinkVo> links,
        List<String> safetyNotes
) {
}
