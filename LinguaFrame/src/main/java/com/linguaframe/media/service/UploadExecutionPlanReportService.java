package com.linguaframe.media.service;

import com.linguaframe.media.domain.vo.UploadExecutionPlanVo;

public interface UploadExecutionPlanReportService {

    String renderMarkdown(UploadExecutionPlanVo plan);
}
