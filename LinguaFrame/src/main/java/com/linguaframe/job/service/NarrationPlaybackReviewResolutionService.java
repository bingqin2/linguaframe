package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;

public interface NarrationPlaybackReviewResolutionService {

    NarrationPlaybackReviewResolutionVo getResolution(String jobId);

    String renderMarkdown(String jobId);
}
