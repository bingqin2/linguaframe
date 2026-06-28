package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoShareSheetVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String readiness,
        String headline,
        String summary,
        List<String> outcomeBullets,
        String recommendedNextAction,
        List<DemoShareSheetLinkVo> links,
        String markdown
) {
}
