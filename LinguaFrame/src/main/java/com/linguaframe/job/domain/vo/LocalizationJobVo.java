package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;
import java.util.List;

public record LocalizationJobVo(
        String jobId,
        String videoId,
        String targetLanguage,
        String ttsVoice,
        String translationStyle,
        String subtitleStylePreset,
        LocalizationJobStatus status,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant failedAt,
        LocalizationJobStage failureStage,
        String failureReason,
        int retryCount,
        JobDispatchEventStatus dispatchStatus,
        int dispatchAttempts,
        Instant dispatchedAt,
        List<JobTimelineEventVo> timelineEvents,
        JobUsageSummaryVo usageSummary,
        JobCacheSummaryVo cacheSummary,
        List<ModelCallVo> modelCalls,
        QualityEvaluationVo qualityEvaluation,
        FailureTriageVo failureTriage,
        JobPipelineProgressVo pipelineProgress
) {
    public LocalizationJobVo(
            String jobId,
            String videoId,
            String targetLanguage,
            String ttsVoice,
            String translationStyle,
            LocalizationJobStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant failedAt,
            LocalizationJobStage failureStage,
            String failureReason,
            int retryCount,
            JobDispatchEventStatus dispatchStatus,
            int dispatchAttempts,
            Instant dispatchedAt,
            List<JobTimelineEventVo> timelineEvents,
            JobUsageSummaryVo usageSummary,
            JobCacheSummaryVo cacheSummary,
            List<ModelCallVo> modelCalls,
            QualityEvaluationVo qualityEvaluation,
            FailureTriageVo failureTriage,
            JobPipelineProgressVo pipelineProgress
    ) {
        this(
                jobId,
                videoId,
                targetLanguage,
                ttsVoice,
                translationStyle,
                "STANDARD",
                status,
                createdAt,
                startedAt,
                completedAt,
                failedAt,
                failureStage,
                failureReason,
                retryCount,
                dispatchStatus,
                dispatchAttempts,
                dispatchedAt,
                timelineEvents,
                usageSummary,
                cacheSummary,
                modelCalls,
                qualityEvaluation,
                failureTriage,
                pipelineProgress
        );
    }

    public LocalizationJobVo(
            String jobId,
            String videoId,
            String targetLanguage,
            String ttsVoice,
            LocalizationJobStatus status,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant failedAt,
            LocalizationJobStage failureStage,
            String failureReason,
            int retryCount,
            JobDispatchEventStatus dispatchStatus,
            int dispatchAttempts,
            Instant dispatchedAt,
            List<JobTimelineEventVo> timelineEvents,
            JobUsageSummaryVo usageSummary,
            JobCacheSummaryVo cacheSummary,
            List<ModelCallVo> modelCalls,
            QualityEvaluationVo qualityEvaluation,
            FailureTriageVo failureTriage,
            JobPipelineProgressVo pipelineProgress
    ) {
        this(
                jobId,
                videoId,
                targetLanguage,
                ttsVoice,
                "NATURAL",
                "STANDARD",
                status,
                createdAt,
                startedAt,
                completedAt,
                failedAt,
                failureStage,
                failureReason,
                retryCount,
                dispatchStatus,
                dispatchAttempts,
                dispatchedAt,
                timelineEvents,
                usageSummary,
                cacheSummary,
                modelCalls,
                qualityEvaluation,
                failureTriage,
                pipelineProgress
        );
    }
}
