package com.linguaframe.media.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.enums.MediaUploadStatus;

import java.time.Instant;

public record MediaUploadVo(
        String videoId,
        String jobId,
        String filename,
        String contentType,
        long fileSizeBytes,
        Integer durationSeconds,
        String sourceObjectKey,
        MediaUploadStatus status,
        LocalizationJobStatus jobStatus,
        String targetLanguage,
        Instant createdAt
) {
}
