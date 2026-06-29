package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.DemoRunVarianceReportVo;

public interface DemoRunVarianceReportService {

    DemoRunVarianceReportVo build(String jobId, String preUploadJson);

    String renderMarkdown(DemoRunVarianceReportVo report);
}
