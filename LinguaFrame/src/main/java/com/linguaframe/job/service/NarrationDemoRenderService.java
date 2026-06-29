package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.RenderNarrationDemoDto;
import com.linguaframe.job.domain.vo.NarrationDemoRenderVo;

public interface NarrationDemoRenderService {

    NarrationDemoRenderVo render(String jobId, RenderNarrationDemoDto request);
}
