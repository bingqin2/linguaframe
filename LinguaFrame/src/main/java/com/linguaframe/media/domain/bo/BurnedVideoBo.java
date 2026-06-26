package com.linguaframe.media.domain.bo;

public record BurnedVideoBo(
        String filename,
        String contentType,
        byte[] content
) {
}
