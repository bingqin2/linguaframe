package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record NarrationStudioVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String overallStatus,
        String phase,
        String recommendedNextAction,
        int segmentCount,
        int characterCount,
        boolean audioReady,
        boolean videoReady,
        List<NarrationStudioStepVo> steps,
        List<NarrationStudioLinkVo> links,
        List<String> safetyNotes
) {
}
