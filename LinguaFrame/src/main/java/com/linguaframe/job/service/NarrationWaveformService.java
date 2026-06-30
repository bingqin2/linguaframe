package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationWaveformVo;

public interface NarrationWaveformService {

    NarrationWaveformVo getWaveform(String jobId, Integer bucketCount);
}
