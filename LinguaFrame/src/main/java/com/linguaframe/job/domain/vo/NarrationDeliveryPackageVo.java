package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record NarrationDeliveryPackageVo(
        String jobId,
        Instant generatedAt,
        String status,
        String phase,
        String recommendedNextAction,
        boolean audioReady,
        boolean videoReady,
        int unresolvedPlaybackCount,
        String evidenceStatus,
        String scriptPackageStatus,
        String renderReviewStatus,
        String playbackReviewStatus,
        String playbackResolutionStatus,
        String recoveryHandoffStatus,
        List<NarrationDeliveryPackageArtifactVo> artifacts,
        List<NarrationDeliveryPackageCheckVo> checks,
        List<NarrationDeliveryPackageLinkVo> safeLinks,
        List<String> packageEntries,
        List<String> safetyNotes
) {
}
