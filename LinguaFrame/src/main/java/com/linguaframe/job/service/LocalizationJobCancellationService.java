package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.LocalizationJobVo;

public interface LocalizationJobCancellationService {

    LocalizationJobVo cancelJob(String jobId);
}
