package com.linguaframe.media.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoUploadReadinessVo(
        String overallStatus,
        String ownerId,
        String demoProfileId,
        Instant generatedAt,
        List<DemoUploadReadinessCheckVo> checks,
        List<String> requiredActions,
        List<String> evidenceRoutes
) {
}
