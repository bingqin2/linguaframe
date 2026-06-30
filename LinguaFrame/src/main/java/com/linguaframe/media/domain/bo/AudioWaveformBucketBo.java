package com.linguaframe.media.domain.bo;

import java.math.BigDecimal;

public record AudioWaveformBucketBo(
        int index,
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal peak,
        BigDecimal rms
) {
}
