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
        List<JobTimelineEventVo> timelineEvents
) {
}
