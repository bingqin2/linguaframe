package com.linguaframe.media.domain.bo;

import java.math.BigDecimal;

public record NarrationWindowBo(
        BigDecimal startSeconds,
        BigDecimal endSeconds,
        BigDecimal duckingVolume,
        BigDecimal narrationVolume,
        Integer fadeDurationMs
) {
    public NarrationWindowBo(BigDecimal startSeconds, BigDecimal endSeconds) {
        this(startSeconds, endSeconds, null, null, null);
    }
}
