package com.linguaframe.job.domain.dto;

public record PublishReviewedSubtitlesRequest(
        String language,
        boolean includeBurnedVideo
) {
}
