package com.linguaframe.media.domain.bo;

public record ExtractedAudioBo(
        String filename,
        String contentType,
        byte[] content
) {
}
