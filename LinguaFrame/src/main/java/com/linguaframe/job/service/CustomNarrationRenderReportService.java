package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.CustomNarrationRenderVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;

public interface CustomNarrationRenderReportService {

    String renderMarkdown(CustomNarrationRenderVo render);

    CustomNarrationRenderVo reportOnly(String jobId, CustomNarrationRenderPreflightVo preflight);
}
