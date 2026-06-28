package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.PublishReviewedSubtitlesRequest;
import com.linguaframe.job.domain.vo.ReviewedSubtitlePublishVo;

public interface ReviewedSubtitleDeliveryService {

    ReviewedSubtitlePublishVo publish(String jobId, PublishReviewedSubtitlesRequest request);
}
