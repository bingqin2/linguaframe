package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;
import java.util.List;

public record DemoRunMonitorVo(
        String jobId,
        String videoId,
        LocalizationJobStatus status,
        JobDispatchEventStatus dispatchStatus,
        Instant generatedAt,
        Long elapsedMs,
        LocalizationJobStage currentStage,
        int completedStageCount,
        int totalStageCount,
        int failedStageCount,
        LocalizationJobStage slowestStage,
        Long slowestStageDurationMs,
        String attentionLevel,
        String summary,
        String recommendedNextAction,
        List<DemoRunMonitorStageVo> stages,
        List<DemoRunMonitorLinkVo> links,
        String markdown
) {
}
