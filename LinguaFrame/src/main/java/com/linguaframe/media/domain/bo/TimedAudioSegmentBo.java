package com.linguaframe.media.domain.bo;

import java.math.BigDecimal;
import java.nio.file.Path;

public record TimedAudioSegmentBo(
        Path inputAudioPath,
        BigDecimal startSeconds,
        BigDecimal endSeconds
) {
}
