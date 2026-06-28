package com.linguaframe.operator.domain.vo;

import java.math.BigDecimal;
import java.util.List;

public record PrivateDemoRunArchiveCandidateVo(
        String jobId,
        String videoId,
        String filename,
        String profileId,
        String status,
        String readiness,
        Integer qualityScore,
        BigDecimal estimatedCostUsd,
        int modelCallCount,
        int providerCacheHitCount,
        boolean handoffReady,
        List<String> roles
) {
}
