package com.linguaframe.job.domain.vo;

import java.util.List;

public record CustomNarrationRenderVo(
        String jobId,
        String status,
        boolean generateNarratedVideo,
        CustomNarrationRenderPreflightVo preflight,
        List<CustomNarrationRenderStepVo> steps,
        NarrationGenerationVo narrationAudio,
        NarratedVideoGenerationVo narratedVideo,
        NarrationRenderReviewVo renderReview,
        NarrationPlaybackReviewVo playbackReview,
        NarrationEvidenceVo evidence,
        NarrationDeliveryPackageVo deliveryPackage,
        int generatedArtifactCount,
        String nextAction
) {
}
