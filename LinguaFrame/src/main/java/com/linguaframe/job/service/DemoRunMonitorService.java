package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.DemoRunMonitorVo;

public interface DemoRunMonitorService {

    DemoRunMonitorVo buildMonitor(String jobId);

    String buildMarkdownMonitor(String jobId);
}
