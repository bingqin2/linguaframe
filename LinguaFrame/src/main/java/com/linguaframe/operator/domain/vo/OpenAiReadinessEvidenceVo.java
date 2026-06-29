package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record OpenAiReadinessEvidenceVo(
        Instant generatedAt,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        List<OpenAiReadinessProviderVo> providers,
        OpenAiReadinessLiveCheckVo liveCheck,
        List<OpenAiReadinessSignalVo> readinessSignals,
        OpenAiReadinessModelUsageVo modelUsage,
        List<OpenAiReadinessCommandVo> commands,
        List<String> safeLinks,
        List<String> safetyNotes
) {
}
