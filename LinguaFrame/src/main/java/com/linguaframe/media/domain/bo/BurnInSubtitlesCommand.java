package com.linguaframe.media.domain.bo;

import java.nio.file.Path;

public record BurnInSubtitlesCommand(
        String jobId,
        Path inputVideoPath,
        Path subtitlePath,
        Path outputVideoPath
) {
}
