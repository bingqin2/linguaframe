package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;

public record RetentionJobCandidateVo(
        String jobId,
        String videoId,
        LocalizationJobStatus status,
        Instant updatedAt
) {
}
