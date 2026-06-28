package com.linguaframe.operator.domain.vo;

import java.util.List;

public record DemoSampleMediaItemVo(
        String id,
        String title,
        String source,
        String sourceUrl,
        String attribution,
        String licenseGuidance,
        String recommendedUse,
        String durationGuidance,
        String command,
        List<String> tags
) {
}
