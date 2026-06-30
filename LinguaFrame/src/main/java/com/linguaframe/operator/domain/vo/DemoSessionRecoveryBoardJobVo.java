package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoSessionRecoveryBoardJobVo(
        String jobId,
        String videoId,
        String filename,
        String demoProfileId,
        String status,
        String currentStage,
        Long elapsedMs,
        Instant createdAt,
        Instant updatedAt,
        String classification,
        String attentionLevel,
        String recoveryClassification,
        String acceptanceStatus,
        String recommendedNextAction,
        List<DemoSessionRecoveryBoardActionVo> actions,
        List<DemoSessionRecoveryBoardLinkVo> links
) {
}
