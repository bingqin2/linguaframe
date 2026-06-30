package com.linguaframe.job.domain.vo;

import java.math.BigDecimal;

public record NarrationWaveformBucketVo(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal peak,
        BigDecimal rms
) {
}
