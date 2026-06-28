package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record PrivateDemoLaunchRehearsalVo(
        Instant generatedAt,
        String overallStatus,
        long readyCount,
        long attentionCount,
        long blockedCount,
        String recommendedNextStepId,
        List<PrivateDemoLaunchRehearsalStepVo> steps,
        List<String> evidenceDownloads,
        String rehearsalNotesMarkdown
) {
}
