package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationGenerationVo;

public interface NarrationAudioService {

    NarrationGenerationVo generateAudio(String jobId);
}
