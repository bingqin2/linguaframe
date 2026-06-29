package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.ApplyNarrationDemoPresetDto;
import com.linguaframe.job.domain.vo.NarrationDemoPresetApplyVo;

public interface NarrationDemoPresetApplyService {

    NarrationDemoPresetApplyVo apply(String jobId, ApplyNarrationDemoPresetDto request);
}
