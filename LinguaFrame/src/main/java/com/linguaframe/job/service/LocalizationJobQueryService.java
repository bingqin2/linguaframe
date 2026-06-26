package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;

public interface LocalizationJobQueryService {

    LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset);

    LocalizationJobVo getJob(String jobId);
}
