package com.linguaframe.media.domain.vo;

import com.linguaframe.media.domain.enums.MediaUploadStatus;

import java.time.Instant;

public record MediaUploadDetailVo(
        String videoId,
        String filename,
        String contentType,
        long fileSizeBytes,
        Integer durationSeconds,
        MediaUploadStatus status,
        Instant createdAt
) {
}
