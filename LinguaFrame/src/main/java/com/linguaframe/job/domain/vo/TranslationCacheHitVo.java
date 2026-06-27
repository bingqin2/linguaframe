package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.bo.TranslationResultBo;

public record TranslationCacheHitVo(
        String cacheKey,
        String sourceJobId,
        TranslationResultBo result
) {
}
