package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.bo.TtsResultBo;

public record TtsCacheHitVo(
        String cacheKey,
        String sourceJobId,
        TtsResultBo result
) {
}
