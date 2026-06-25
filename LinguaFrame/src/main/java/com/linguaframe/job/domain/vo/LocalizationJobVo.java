package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;

public record LocalizationJobVo(
        String jobId,
        String videoId,
        String targetLanguage,
        LocalizationJobStatus status,
        Instant createdAt,
        JobDispatchEventStatus dispatchStatus,
        int dispatchAttempts,
        Instant dispatchedAt
) {
}
