package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoReviewerWorkspaceVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        Instant completedAt,
        String targetLanguage,
        String demoProfileId,
        List<DemoReviewerWorkspaceSectionVo> sections,
        List<DemoReviewerWorkspaceCheckVo> checks,
        List<DemoReviewerWorkspaceLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
