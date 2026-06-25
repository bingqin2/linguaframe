package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.JobDispatchResultVo;

public interface JobDispatchService {

    JobDispatchResultVo dispatchReadyEvents(int limit);
}
