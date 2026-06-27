package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.ModelCallOperation;

public record ProviderCacheHitVo(
        ModelCallOperation operation,
        String cacheKey,
        String sourceJobId
) {
}
