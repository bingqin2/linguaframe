package com.linguaframe.media.service;

import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;

public interface DemoUploadReadinessService {

    DemoUploadReadinessVo getReadiness(String demoProfileId);
}
