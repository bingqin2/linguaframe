package com.linguaframe.job.domain.bo;

public record TtsRequestBo(
        String jobId,
        String language,
        String text
) {
}
