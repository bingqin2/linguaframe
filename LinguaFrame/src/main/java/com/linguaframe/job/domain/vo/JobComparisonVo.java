package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record JobComparisonVo(
        String baselineJobId,
        String comparisonJobId,
        boolean sameVideo,
        Instant generatedAt,
        JobComparisonJobVo baseline,
        JobComparisonJobVo comparison,
        JobComparisonDeltaVo delta,
        List<JobComparisonSettingDiffVo> settingDiffs
) {
}
