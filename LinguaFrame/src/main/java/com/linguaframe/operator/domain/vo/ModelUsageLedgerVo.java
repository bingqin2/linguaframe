package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record ModelUsageLedgerVo(
        Instant generatedAt,
        int limit,
        String ownerId,
        String ownershipScope,
        ModelUsageLedgerSummaryVo summary,
        List<ModelUsageLedgerJobVo> jobs,
        List<ModelUsageLedgerOperationVo> operations,
        List<ModelUsageLedgerCallVo> recentCalls,
        List<String> safeLinks,
        List<String> safetyNotes
) {
}
