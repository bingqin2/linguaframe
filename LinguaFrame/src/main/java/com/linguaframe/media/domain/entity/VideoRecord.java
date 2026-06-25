package com.linguaframe.media.domain.entity;

import com.linguaframe.media.domain.enums.MediaUploadStatus;

import java.time.Instant;

public record VideoRecord(
        String id,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        String sourceObjectKey,
        MediaUploadStatus status,
        Instant createdAt
) {
}
