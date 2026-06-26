package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;

public interface TranscriptionProvider {

    TranscriptionResultBo transcribe(String jobId, byte[] audioContent);
}
