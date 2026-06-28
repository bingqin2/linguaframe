package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record PrivateDemoRunArchiveVo(
        Instant generatedAt,
        String overallStatus,
        String recommendedJobId,
        String recommendedVideoId,
        String recommendedProfileId,
        String recommendedReadiness,
        String operationsOverallStatus,
        String launchOverallStatus,
        String launchRecommendedNextStep,
        int galleryCompletedJobCount,
        int galleryHandoffReadyCount,
        List<PrivateDemoRunArchiveCandidateVo> candidates,
        List<PrivateDemoRunArchiveLinkVo> archiveLinks,
        String archiveNotesMarkdown
) {
}
