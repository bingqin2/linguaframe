package com.linguaframe.media.domain.bo;

import java.math.BigDecimal;

public record NarrationWindowBo(
        BigDecimal startSeconds,
        BigDecimal endSeconds
) {
}
