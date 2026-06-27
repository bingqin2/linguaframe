package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;
import com.linguaframe.job.domain.vo.QualityEvaluationCacheHitVo;

import java.util.Optional;

public interface QualityEvaluationCacheService {

    Optional<QualityEvaluationCacheHitVo> findCachedEvaluation(QualityEvaluationCacheLookupBo lookup);

    void storeEvaluation(QualityEvaluationCacheLookupBo lookup, String jobId, QualityEvaluationResultBo result);
}
