package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record NarrationEvidenceVo(
        String jobId,
        String status,
        int segmentCount,
        int totalCharacterCount,
        BigDecimal totalTimelineDurationSeconds,
        boolean narrationAudioReady,
        int audioArtifactCount,
        List<NarrationEvidenceCheckVo> checks,
        List<NarrationEvidenceLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
