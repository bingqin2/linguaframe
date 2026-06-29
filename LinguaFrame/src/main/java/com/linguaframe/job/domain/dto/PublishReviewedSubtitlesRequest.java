package com.linguaframe.job.domain.dto;

public record PublishReviewedSubtitlesRequest(
        String language,
        boolean includeBurnedVideo,
        String releaseNotes
) {
    public PublishReviewedSubtitlesRequest(String language, boolean includeBurnedVideo) {
        this(language, includeBurnedVideo, null);
    }
}
