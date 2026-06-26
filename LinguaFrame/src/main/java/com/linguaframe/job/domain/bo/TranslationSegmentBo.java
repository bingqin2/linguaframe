package com.linguaframe.job.domain.bo;

public record TranslationSegmentBo(
        int index,
        long startMs,
        long endMs,
        String text
) {
}
