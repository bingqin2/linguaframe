package com.linguaframe.media.domain.bo;

import java.nio.file.Path;

public record MediaDurationProbeCommand(
        String filename,
        Path inputVideoPath
) {
}
