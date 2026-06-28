package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;

public record SubtitlePolishingCacheHitVo(
        String cacheKey,
        String sourceJobId,
        SubtitlePolishingResultBo result
) {
}
