package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;

import java.util.List;

public interface QualityEvaluationCacheKeyService {

    QualityEvaluationCacheLookupBo build(
            String language,
            String provider,
            String model,
            String promptVersion,
            List<TranscriptSegmentVo> sourceSegments,
            List<SubtitleSegmentVo> targetSegments
    );
}
