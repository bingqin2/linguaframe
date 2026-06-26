package com.linguaframe.job.domain.bo;

public record TranscriptionSegmentBo(
        int index,
        long startMs,
        long endMs,
        String text
) {
}
