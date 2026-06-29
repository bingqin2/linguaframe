package com.linguaframe.media.domain.bo;

import java.nio.file.Path;
import java.util.List;

public record CreateTimedAudioBedCommand(
        String jobId,
        List<TimedAudioSegmentBo> segments,
        Path outputAudioPath,
        String outputFilename
) {
}
