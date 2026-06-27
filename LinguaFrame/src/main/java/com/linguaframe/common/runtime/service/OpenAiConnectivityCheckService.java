package com.linguaframe.common.runtime.service;

import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;

public interface OpenAiConnectivityCheckService {

    RuntimeProbeResultVo check();
}
