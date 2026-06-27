package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;

public record TranscriptionCacheHitVo(
        String cacheKey,
        String sourceJobId,
        TranscriptionResultBo result
) {
}
