package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.time.Instant;
import java.util.List;

public record DeliveryManifestVo(
        String jobId,
        String videoId,
        String targetLanguage,
        String subtitleStylePreset,
        int translationGlossaryEntryCount,
        String translationGlossaryHash,
        LocalizationJobStatus status,
        Instant generatedAt,
        boolean handoffReady,
        int reviewedSubtitleArtifactCount,
        boolean reviewedBurnedVideoAvailable,
        int generatedArtifactCount,
        List<DeliveryManifestArtifactVo> reviewedArtifacts,
        List<DeliveryManifestArtifactVo> auditArtifacts,
        List<DeliveryManifestLinkVo> links
) {
    public DeliveryManifestVo(
            String jobId,
            String videoId,
            String targetLanguage,
            String subtitleStylePreset,
            LocalizationJobStatus status,
            Instant generatedAt,
            boolean handoffReady,
            int reviewedSubtitleArtifactCount,
            boolean reviewedBurnedVideoAvailable,
            int generatedArtifactCount,
            List<DeliveryManifestArtifactVo> reviewedArtifacts,
            List<DeliveryManifestArtifactVo> auditArtifacts,
            List<DeliveryManifestLinkVo> links
    ) {
        this(
                jobId,
                videoId,
                targetLanguage,
                subtitleStylePreset,
                0,
                "",
                status,
                generatedAt,
                handoffReady,
                reviewedSubtitleArtifactCount,
                reviewedBurnedVideoAvailable,
                generatedArtifactCount,
                reviewedArtifacts,
                auditArtifacts,
                links
        );
    }
}
