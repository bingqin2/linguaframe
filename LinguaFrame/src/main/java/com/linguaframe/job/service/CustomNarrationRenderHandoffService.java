package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.CustomNarrationRenderHandoffVo;

public interface CustomNarrationRenderHandoffService {

    CustomNarrationRenderHandoffVo summarize(String jobId);
}
