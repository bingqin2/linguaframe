package com.linguaframe.job.domain.bo;

import java.util.List;

public record TranscriptionResultBo(
        List<TranscriptionSegmentBo> segments
) {
}
