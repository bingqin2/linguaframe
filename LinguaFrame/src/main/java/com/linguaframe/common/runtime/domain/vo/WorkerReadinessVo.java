package com.linguaframe.common.runtime.domain.vo;

import com.linguaframe.job.domain.enums.WorkerRole;

import java.util.List;

public record WorkerReadinessVo(
        boolean dispatchEnabled,
        boolean executionEnabled,
        WorkerRole role,
        int maxRetries,
        int dispatchBatchSize,
        long dispatchIntervalMs,
        String listenerQueue,
        String jobExchange,
        String defaultJobQueue,
        String defaultRoutingKey,
        String ffmpegJobQueue,
        String ffmpegRoutingKey,
        String openaiJobQueue,
        String openaiRoutingKey,
        List<String> ownedStageGroups,
        List<String> recommendedCommands
) {
}
