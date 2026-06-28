package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record PrivateDemoOperationsVo(
        Instant generatedAt,
        String overallStatus,
        long readyCount,
        long attentionCount,
        long blockedCount,
        List<PrivateDemoOperationsSectionVo> sections,
        List<PrivateDemoOperationsCommandVo> commands,
        List<PrivateDemoOperationsLinkVo> documentationLinks
) {
}
