package com.linguaframe.media.domain.entity;

import com.linguaframe.media.domain.enums.MediaUploadStatus;

import java.time.Instant;

public record VideoRecord(
        String id,
        String ownerId,
        String originalFilename,
        String contentType,
        long fileSizeBytes,
        Integer durationSeconds,
        String sourceContentSha256,
        String sourceObjectKey,
        MediaUploadStatus status,
        Instant createdAt
) {
    public VideoRecord(
            String id,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            String sourceObjectKey,
            MediaUploadStatus status,
            Instant createdAt
    ) {
        this(id, "demo-owner", originalFilename, contentType, fileSizeBytes, null, null, sourceObjectKey, status, createdAt);
    }

    public VideoRecord(
            String id,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Integer durationSeconds,
            String sourceObjectKey,
            MediaUploadStatus status,
            Instant createdAt
    ) {
        this(id, "demo-owner", originalFilename, contentType, fileSizeBytes, durationSeconds, null, sourceObjectKey, status, createdAt);
    }

    public VideoRecord(
            String id,
            String ownerId,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Integer durationSeconds,
            String sourceObjectKey,
            MediaUploadStatus status,
            Instant createdAt
    ) {
        this(id, ownerId, originalFilename, contentType, fileSizeBytes, durationSeconds, null, sourceObjectKey, status, createdAt);
    }
}
