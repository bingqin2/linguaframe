package com.linguaframe.media.domain.bo;

import java.nio.file.Path;

public record BurnInSubtitlesCommand(
        String jobId,
        Path inputVideoPath,
        Path subtitlePath,
        Path outputVideoPath,
        String subtitleStylePreset
) {
    public BurnInSubtitlesCommand(
            String jobId,
            Path inputVideoPath,
            Path subtitlePath,
            Path outputVideoPath
    ) {
        this(jobId, inputVideoPath, subtitlePath, outputVideoPath, "STANDARD");
    }
}
