package com.linguaframe.media.domain.bo;

public record DubbedVideoBo(
        String filename,
        String contentType,
        byte[] content
) {
}
