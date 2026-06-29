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
        Instant createdAt,
        String jobDetailHref,
        String shareSheetHref,
        String evidenceHref,
        String demoRunPackageHref,
        String acceptanceGateHref
) {
    public UploadSourceReuseCandidateVo(
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
        this(
                videoId,
                jobId,
                originalFilename,
                durationSeconds,
                jobStatus,
                demoProfileId,
                translationStyle,
                subtitleStylePreset,
                subtitlePolishingMode,
                createdAt,
                null,
                null,
                null,
                null,
                null
        );
    }
}
