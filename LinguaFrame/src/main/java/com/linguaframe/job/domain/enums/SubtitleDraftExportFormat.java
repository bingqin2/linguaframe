package com.linguaframe.job.domain.enums;

import org.springframework.http.MediaType;

public enum SubtitleDraftExportFormat {
    JSON("json", MediaType.APPLICATION_JSON_VALUE),
    SRT("srt", "application/x-subrip;charset=UTF-8"),
    VTT("vtt", "text/vtt;charset=UTF-8");

    private final String extension;
    private final String contentType;

    SubtitleDraftExportFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }
}
