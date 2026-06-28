package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record SubtitleDraftSummaryVo(
        String jobId,
        String targetLanguage,
        int segmentCount,
        int editedSegmentCount,
        Instant lastUpdatedAt,
        List<SubtitleDraftSegmentVo> segments
) {
}
