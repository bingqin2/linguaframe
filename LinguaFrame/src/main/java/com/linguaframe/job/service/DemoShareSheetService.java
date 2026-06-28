package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.DemoShareSheetVo;

public interface DemoShareSheetService {

    DemoShareSheetVo buildShareSheet(String jobId);

    String buildMarkdownShareSheet(String jobId);
}
