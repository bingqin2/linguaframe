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
        CustomNarrationRenderHandoffVo customNarrationRender,
        List<DemoAcceptanceGateRunbookStepVo> runbookSteps,
        List<DemoAcceptanceGateCheckVo> checks,
        List<DemoAcceptanceGateEvidenceVo> evidence,
        List<DemoAcceptanceGateLinkVo> links,
        List<String> safetyNotes
) {
    public DemoAcceptanceGateVo(
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
            List<DemoAcceptanceGateRunbookStepVo> runbookSteps,
            List<DemoAcceptanceGateCheckVo> checks,
            List<DemoAcceptanceGateEvidenceVo> evidence,
            List<DemoAcceptanceGateLinkVo> links,
            List<String> safetyNotes
    ) {
        this(
                jobId,
                videoId,
                generatedAt,
                gateStatus,
                jobStatus,
                targetLanguage,
                demoProfileId,
                headline,
                summary,
                recommendedNextAction,
                defaultCustomNarrationRender(jobId),
                runbookSteps,
                checks,
                evidence,
                links,
                safetyNotes
        );
    }

    private static CustomNarrationRenderHandoffVo defaultCustomNarrationRender(String jobId) {
        return new CustomNarrationRenderHandoffVo(
                jobId,
                "NOT_APPLICABLE",
                "No saved custom narration rows",
                0,
                0,
                false,
                false,
                "/api/jobs/" + jobId + "/custom-narration-render/markdown/download",
                "/api/jobs/" + jobId + "/custom-narration-render",
                "/api/jobs/" + jobId + "/narration-evidence",
                "/api/jobs/" + jobId + "/narration-delivery-package",
                "Add or import custom narration rows before rendering."
        );
    }
}
