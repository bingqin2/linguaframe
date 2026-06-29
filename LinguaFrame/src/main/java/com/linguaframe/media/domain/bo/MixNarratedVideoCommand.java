package com.linguaframe.media.domain.bo;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;

public record MixNarratedVideoCommand(
        String jobId,
        Path inputVideoPath,
        Path narrationAudioPath,
        Path outputVideoPath,
        String outputFilename,
        BigDecimal duckingVolume,
        List<NarrationWindowBo> narrationWindows
) {
}
