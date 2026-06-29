package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarratedVideoGenerationVo;

public interface NarratedVideoService {

    NarratedVideoGenerationVo generateVideo(String jobId);
}
