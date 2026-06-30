package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoSessionRecoveryBoardVo(
        Instant generatedAt,
        String overallStatus,
        String headline,
        String recommendedNextAction,
        int recoverNowCount,
        int watchCount,
        int readyCount,
        int needsReviewCount,
        int noActionCount,
        DemoSessionRecoveryBoardActionVo primaryAction,
        List<DemoSessionRecoveryBoardJobVo> jobs,
        List<DemoSessionRecoveryBoardCheckVo> checks,
        List<DemoSessionRecoveryBoardLinkVo> links,
        List<String> safetyNotes,
        String markdown
) {
}
