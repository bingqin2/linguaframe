package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;
import java.util.List;

public record StuckJobRecoveryVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String status,
        String attentionLevel,
        String classification,
        String headline,
        String recommendedNextAction,
        LocalizationJobStatus jobStatus,
        JobDispatchEventStatus dispatchStatus,
        int dispatchAttempts,
        Instant dispatchedAt,
        Instant lastTimelineAt,
        long ageSeconds,
        long staleSeconds,
        List<StuckJobRecoveryCheckVo> checks,
        List<StuckJobRecoveryActionVo> actions,
        List<StuckJobRecoveryLinkVo> safeLinks,
        List<String> safetyNotes,
        String markdown
) {
}
