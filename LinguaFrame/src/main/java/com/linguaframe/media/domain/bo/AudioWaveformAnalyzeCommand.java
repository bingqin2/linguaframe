package com.linguaframe.media.domain.bo;

import java.nio.file.Path;

public record AudioWaveformAnalyzeCommand(
        Path inputMediaPath,
        int bucketCount,
        double durationSeconds
) {
}
