package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStage;

import java.util.List;

public record JobPipelineProgressVo(
        int totalStageCount,
        int completedStageCount,
        int failedStageCount,
        int skippedStageCount,
        int cacheHitStageCount,
        LocalizationJobStage currentStage,
        boolean terminal,
        long totalMeasuredDurationMs,
        LocalizationJobStage slowestStage,
        Long slowestStageDurationMs,
        List<JobStageProgressVo> stages
) {
}
