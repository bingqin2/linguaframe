package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record OpenAiSmokeProofVo(
        String jobId,
        String videoId,
        String targetLanguage,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        Instant completedAt,
        List<OpenAiSmokeProofCheckVo> requiredChecks,
        List<OpenAiSmokeProofCheckVo> optionalChecks,
        List<OpenAiSmokeProofCallVo> modelCalls,
        List<OpenAiSmokeProofArtifactVo> artifacts,
        List<OpenAiSmokeProofLinkVo> safeLinks,
        List<String> safetyNotes
) {
}
