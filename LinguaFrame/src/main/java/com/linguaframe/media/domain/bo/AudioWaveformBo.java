package com.linguaframe.media.domain.bo;

import java.math.BigDecimal;
import java.util.List;

public record AudioWaveformBo(
        int bucketCount,
        BigDecimal durationSeconds,
        List<AudioWaveformBucketBo> buckets
) {
}
