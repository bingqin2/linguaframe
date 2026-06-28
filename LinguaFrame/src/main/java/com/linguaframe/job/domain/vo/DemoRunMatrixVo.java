package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoRunMatrixVo(
        String anchorJobId,
        String videoId,
        Instant generatedAt,
        List<DemoRunMatrixJobVo> jobs,
        String recommendedBaselineJobId,
        String bestQualityJobId,
        String lowestCostJobId
) {
}
