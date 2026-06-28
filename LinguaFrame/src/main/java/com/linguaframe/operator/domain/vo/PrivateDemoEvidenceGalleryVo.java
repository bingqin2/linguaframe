package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record PrivateDemoEvidenceGalleryVo(
        Instant generatedAt,
        String overallStatus,
        int completedJobCount,
        int handoffReadyCount,
        String recommendedJobId,
        List<PrivateDemoEvidenceGalleryJobVo> jobs,
        List<PrivateDemoEvidenceGalleryDownloadVo> galleryDownloads,
        String galleryNotesMarkdown
) {
}
