package com.linguaframe.media.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;

public record UploadSourceReuseCandidateVo(
        String videoId,
        String jobId,
        String originalFilename,
        Integer durationSeconds,
        LocalizationJobStatus jobStatus,
        String demoProfileId,
        String translationStyle,
        String subtitleStylePreset,
        String subtitlePolishingMode,
        Instant createdAt
) {
}
