package com.linguaframe.media.domain.vo;

import com.linguaframe.common.quota.OwnerQuotaPreflightVo;

import java.time.Instant;
import java.util.List;

public record UploadDecisionPackageVo(
        Instant generatedAt,
        String overallStatus,
        String recommendedDecision,
        String recommendedNextAction,
        UploadExecutionPlanVo executionPlan,
        OwnerQuotaPreflightVo ownerQuotaPreflight,
        DemoUploadReadinessVo uploadReadiness,
        String executionPlanMarkdown,
        List<String> safetyNotes
) {
}
