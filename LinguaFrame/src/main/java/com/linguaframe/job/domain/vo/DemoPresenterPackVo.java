package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoPresenterPackVo(
        String anchorJobId,
        String videoId,
        Instant generatedAt,
        String headline,
        String readinessStatus,
        String recommendedBaselineJobId,
        String bestQualityJobId,
        String lowestCostJobId,
        List<DemoPresenterPackRunVo> runs,
        List<DemoPresenterPackDownloadVo> downloads,
        String presenterNotesMarkdown
) {
}
