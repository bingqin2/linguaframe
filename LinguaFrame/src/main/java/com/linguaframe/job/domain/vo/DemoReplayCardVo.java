package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DemoReplayCardVo(
        String jobId,
        String videoId,
        Instant generatedAt,
        String headline,
        String readiness,
        LocalizationJobStatus status,
        String targetLanguage,
        String demoProfileId,
        Integer qualityScore,
        String qualityVerdict,
        int modelCallCount,
        int providerCacheHitCount,
        int artifactCacheHitCount,
        BigDecimal estimatedCostUsd,
        String recommendedBaselineJobId,
        String bestQualityJobId,
        String lowestCostJobId,
        List<DemoReplayCardSettingVo> settings,
        List<DemoReplayCardCommandVo> commands,
        List<DemoReplayCardLinkVo> links,
        List<String> safetyNotes
) {
}
