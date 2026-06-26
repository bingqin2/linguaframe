package com.linguaframe.media.domain.bo;

import java.nio.file.Path;

public record ExtractAudioCommand(
        String jobId,
        Path inputVideoPath,
        Path outputAudioPath
) {
}
