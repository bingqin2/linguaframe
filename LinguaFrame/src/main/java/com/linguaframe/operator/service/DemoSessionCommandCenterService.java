package com.linguaframe.operator.service;

import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;

public interface DemoSessionCommandCenterService {

    DemoSessionCommandCenterVo commandCenter(String jobId);

    String commandCenterMarkdown(String jobId);
}
