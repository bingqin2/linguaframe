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
        NarrationMixAutomationVo mixAutomation,
        NarrationVoiceCatalogVo voiceCatalog,
        NarrationTimelineSummaryVo timeline,
        List<NarrationSegmentVo> segments,
        List<String> safetyNotes
) {
    public NarrationWorkspaceVo(
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
        this(
                jobId,
                status,
                segmentCount,
                totalDurationSeconds,
                totalCharacterCount,
                generationReady,
                mixSettings,
                new NarrationMixAutomationVo(0, 0, 0, 0, List.of(), List.of()),
                voiceCatalog,
                timeline,
                segments,
                safetyNotes
        );
    }
}
