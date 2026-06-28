package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredAiAuditPackageBo;

public interface AiAuditPackageService {

    StoredAiAuditPackageBo openAiAuditPackage(String jobId);
}
