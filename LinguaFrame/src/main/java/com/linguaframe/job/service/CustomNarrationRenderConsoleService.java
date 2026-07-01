package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.CustomNarrationRenderPreflightDto;
import com.linguaframe.job.domain.dto.CustomNarrationRenderDto;
import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderVo;

public interface CustomNarrationRenderConsoleService {

    CustomNarrationRenderPreflightVo preflight(String jobId, CustomNarrationRenderPreflightDto request);

    CustomNarrationRenderVo render(String jobId, CustomNarrationRenderDto request);
}
