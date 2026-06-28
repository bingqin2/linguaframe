package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredQualityEvidenceBo;

public interface QualityEvaluationEvidenceService {

    StoredQualityEvidenceBo openMarkdownEvidence(String jobId);
}
