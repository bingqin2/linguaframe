package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredNarrationRecoveryHandoffPackageBo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffVo;

public interface NarrationRecoveryHandoffService {

    NarrationRecoveryHandoffVo getHandoff(String jobId);

    String renderMarkdown(String jobId);

    StoredNarrationRecoveryHandoffPackageBo openPackage(String jobId);
}
