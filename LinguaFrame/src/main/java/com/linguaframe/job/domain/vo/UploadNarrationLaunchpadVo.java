package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record UploadNarrationLaunchpadVo(
        String jobId,
        Instant generatedAt,
        String status,
        String nextAction,
        int segmentCount,
        int characterCount,
        BigDecimal totalNarrationSeconds,
        Integer selectedSegmentIndex,
        String voiceProvider,
        String defaultVoice,
        String voiceSummary,
        String sceneBoardStatus,
        int blockingIssueCount,
        int attentionIssueCount,
        boolean audioReady,
        boolean videoReady,
        List<UploadNarrationLaunchpadActionVo> actions,
        List<NarrationSceneBoardLinkVo> safeLinks,
        List<String> safetyNotes
) {
    public UploadNarrationLaunchpadVo {
        actions = actions == null ? List.of() : List.copyOf(actions);
        safeLinks = safeLinks == null ? List.of() : List.copyOf(safeLinks);
        safetyNotes = safetyNotes == null ? List.of() : List.copyOf(safetyNotes);
    }
}
