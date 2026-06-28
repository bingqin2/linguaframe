package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredHandoffPackageBo;

public interface JobHandoffPackageService {

    StoredHandoffPackageBo openHandoffPackage(String jobId);
}
