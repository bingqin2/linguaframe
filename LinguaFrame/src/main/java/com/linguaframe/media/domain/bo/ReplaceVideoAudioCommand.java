package com.linguaframe.media.domain.bo;

import java.nio.file.Path;

public record ReplaceVideoAudioCommand(
        String jobId,
        Path inputVideoPath,
        Path inputAudioPath,
        Path outputVideoPath
) {
}
