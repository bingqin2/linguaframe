package com.linguaframe.job.domain.dto;

import java.util.List;

public record UpdateSubtitleDraftRequest(
        List<Segment> segments
) {

    public record Segment(
            int index,
            String text
    ) {
    }
}
