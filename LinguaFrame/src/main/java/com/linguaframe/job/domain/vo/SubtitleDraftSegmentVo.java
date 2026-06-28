package com.linguaframe.job.domain.vo;

import java.time.Instant;

public record SubtitleDraftSegmentVo(
        int index,
        long startMs,
        long endMs,
        String sourceText,
        String generatedText,
        String draftText,
        boolean edited,
        Instant updatedAt
) {
}
