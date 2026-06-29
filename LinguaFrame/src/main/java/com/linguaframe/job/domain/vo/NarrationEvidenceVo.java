package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationEvidenceVo(
        String jobId,
        String status,
        int segmentCount,
        int totalCharacterCount,
        BigDecimal totalTimelineDurationSeconds,
        int timelineGapCount,
        BigDecimal timelineGapSeconds,
        boolean timelineHasOverlap,
        boolean narrationAudioReady,
        int audioArtifactCount,
        String audioLayout,
        boolean timeAligned,
        boolean narratedVideoReady,
        int narratedVideoArtifactCount,
        String mixMode,
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        int fadeDurationMs,
        String mixSettingsSource,
        List<NarrationEvidenceCheckVo> checks,
        List<NarrationEvidenceLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
