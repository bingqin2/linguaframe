package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;

public interface OpenAiSmokeProofService {

    OpenAiSmokeProofVo getProof(String jobId);

    String renderMarkdown(String jobId);
}
