package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.LocalizationJobVo;

public interface LocalizationJobRetryService {

    LocalizationJobVo retryFailedJob(String jobId);
}
