package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.bo.QualityEvaluationResultBo;

public record QualityEvaluationCacheHitVo(
        String cacheKey,
        String sourceJobId,
        QualityEvaluationResultBo result
) {
}
