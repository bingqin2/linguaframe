package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.LocalizationJobVo;

public interface LocalizationJobQueryService {

    LocalizationJobVo getJob(String jobId);
}
