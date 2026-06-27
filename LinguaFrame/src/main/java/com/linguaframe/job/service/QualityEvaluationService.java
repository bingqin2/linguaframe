package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;

import java.util.List;
import java.util.Optional;

public interface QualityEvaluationService {

    QualityEvaluationVo evaluateAndStore(
            String jobId,
            String language,
            List<TranscriptSegmentVo> sourceSegments,
            List<SubtitleSegmentVo> targetSegments
    );

    QualityEvaluationVo storeCachedEvaluation(String jobId, String language, QualityEvaluationResultBo result);

    Optional<QualityEvaluationVo> latestForJob(String jobId);
}
