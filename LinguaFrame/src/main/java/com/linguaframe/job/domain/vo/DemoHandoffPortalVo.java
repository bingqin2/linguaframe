package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoHandoffPortalVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String overallStatus,
        String phase,
        String headline,
        String recommendedNextAction,
        Instant completedAt,
        String targetLanguage,
        String demoProfileId,
        List<DemoHandoffPortalCheckVo> checks,
        List<DemoHandoffPortalSectionVo> sections,
        List<DemoHandoffPortalLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
