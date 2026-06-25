package com.linguaframe.media.domain.vo;

import com.linguaframe.media.domain.enums.MediaUploadValidationCode;

import java.util.List;

public record MediaUploadValidationVo(
        boolean valid,
        MediaUploadValidationCode code,
        String message,
        String filename,
        String contentType,
        long fileSizeBytes,
        long maxFileSizeBytes,
        int maxDurationSeconds,
        List<String> supportedContentTypes
) {
}
