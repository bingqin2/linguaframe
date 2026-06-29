package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;

public interface NarrationEvidenceService {

    NarrationEvidenceVo getEvidence(String jobId);

    String renderMarkdown(String jobId);

    StoredNarrationEvidencePackageBo openPackage(String jobId);
}
