package com.linguaframe.job.domain.vo;

import java.util.List;

public record ReviewedSubtitlePublishVo(
        String jobId,
        String targetLanguage,
        boolean burnedVideoRequested,
        boolean burnedVideoCreated,
        int releaseNotesLength,
        List<SubtitleReviewEvidenceCategoryVo> reviewDecisionCounts,
        List<SubtitleReviewEvidenceCategoryVo> issueCategoryCounts,
        List<JobArtifactVo> artifacts
) {
    public ReviewedSubtitlePublishVo(
            String jobId,
            String targetLanguage,
            boolean burnedVideoRequested,
            boolean burnedVideoCreated,
            List<JobArtifactVo> artifacts
    ) {
        this(jobId, targetLanguage, burnedVideoRequested, burnedVideoCreated, 0, List.of(), List.of(), artifacts);
    }
}
