package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoRunLauncherVo(
        Instant generatedAt,
        String overallStatus,
        String recommendedSampleId,
        String recommendedProfileId,
        String recommendedNextCommand,
        List<DemoRunLauncherGateVo> gates,
        List<DemoRunLauncherCommandVo> commands,
        List<DemoRunLauncherEvidenceVo> expectedEvidence,
        String notesMarkdown
) {
}
