package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.StuckJobRecoveryVo;

public interface StuckJobRecoveryService {

    StuckJobRecoveryVo recovery(String jobId);

    String recoveryMarkdown(String jobId);

    StuckJobRecoveryVo runAction(String jobId, String actionId, String confirmation);
}
