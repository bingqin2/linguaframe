package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;

public interface NarrationRenderReviewService {

    NarrationRenderReviewVo getReview(String jobId);

    String renderMarkdown(String jobId);
}
