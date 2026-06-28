package com.linguaframe.operator.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoSampleMediaCatalogVo(
        Instant generatedAt,
        String overallStatus,
        int uploadDurationLimitSeconds,
        String recommendedSampleId,
        List<DemoSampleMediaItemVo> items,
        List<DemoSampleMediaConfiguredPathVo> configuredPaths,
        List<DemoSampleMediaCommandVo> commands,
        String notesMarkdown,
        List<PrivateDemoOperationsLinkVo> documentationLinks
) {
}
