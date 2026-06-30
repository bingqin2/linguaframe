package com.linguaframe.job.domain.vo;

public record NarrationSegmentPreviewVo(
        byte[] audioContent,
        String filename,
        String contentType,
        String voice,
        int characterCount,
        String safetyNote
) {
}
