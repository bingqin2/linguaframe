package com.linguaframe.job.domain.bo;

public record TtsResultBo(
        byte[] audioContent,
        String filename,
        String contentType
) {
}
