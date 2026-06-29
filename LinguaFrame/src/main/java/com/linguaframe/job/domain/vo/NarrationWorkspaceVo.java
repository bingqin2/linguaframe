package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationWorkspaceVo(
        String jobId,
        String status,
        int segmentCount,
        BigDecimal totalDurationSeconds,
        int totalCharacterCount,
        boolean generationReady,
        NarrationMixSettingsVo mixSettings,
        NarrationVoiceCatalogVo voiceCatalog,
        NarrationTimelineSummaryVo timeline,
        List<NarrationSegmentVo> segments,
        List<String> safetyNotes
) {
}
