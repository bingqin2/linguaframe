package com.linguaframe.operator.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PrivateDemoEvidenceGalleryJobVo(
        String jobId,
        String videoId,
        String filename,
        String targetLanguage,
        String demoProfileId,
        LocalizationJobStatus status,
        Instant createdAt,
        Instant completedAt,
        Integer qualityScore,
        String qualityVerdict,
        BigDecimal estimatedCostUsd,
        int modelCallCount,
        int providerCacheHitCount,
        boolean handoffReady,
        boolean presenterPackReady,
        boolean recommended,
        List<String> attentionReasons,
        List<PrivateDemoEvidenceGalleryDownloadVo> downloads
) {
}
