package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredEvidenceBundleBo;

public interface JobEvidenceBundleService {

    StoredEvidenceBundleBo openEvidenceBundle(String jobId);
}
