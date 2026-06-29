package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationScriptPackageVo(
        String jobId,
        String targetLanguage,
        BigDecimal durationSeconds,
        String status,
        int segmentCount,
        int totalCharacterCount,
        BigDecimal totalTimelineDurationSeconds,
        int timelineGapCount,
        BigDecimal timelineGapSeconds,
        boolean timelineHasOverlap,
        String voiceSummary,
        String defaultVoice,
        NarrationMixSettingsVo mixSettings,
        NarrationVoiceCatalogVo voiceCatalog,
        List<NarrationScriptPackageSegmentVo> segments,
        List<NarrationScriptPackageCheckVo> checks,
        List<NarrationScriptPackageLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
