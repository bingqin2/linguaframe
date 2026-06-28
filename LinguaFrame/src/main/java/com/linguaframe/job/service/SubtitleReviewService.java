package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;

public interface SubtitleReviewService {

    SubtitleReviewSummaryVo buildReview(String jobId, String language);
}
