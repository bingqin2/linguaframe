package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.NarrationDemoRenderPreflightRequestDto;
import com.linguaframe.job.domain.vo.NarrationDemoRenderPreflightVo;

public interface NarrationDemoRenderPreflightService {

    NarrationDemoRenderPreflightVo preflight(String jobId, NarrationDemoRenderPreflightRequestDto request);
}
