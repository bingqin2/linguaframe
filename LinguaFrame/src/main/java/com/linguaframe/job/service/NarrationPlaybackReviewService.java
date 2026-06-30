package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.UpdateNarrationPlaybackReviewSegmentDto;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;

public interface NarrationPlaybackReviewService {

    NarrationPlaybackReviewVo getReview(String jobId);

    NarrationPlaybackReviewVo updateSegmentReview(String jobId, int segmentIndex, UpdateNarrationPlaybackReviewSegmentDto request);

    String renderMarkdown(String jobId);
}
