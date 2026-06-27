package com.linguaframe.job.domain.bo;

public record TtsRequestBo(
        String jobId,
        String language,
        String voice,
        String text
) {
    public TtsRequestBo(String jobId, String language, String text) {
        this(jobId, language, null, text);
    }
}
