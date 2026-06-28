package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record DemoRunSnapshotVo(
        String jobId,
        String videoId,
        String targetLanguage,
        String demoProfileId,
        Instant generatedAt,
        String readiness,
        String headline,
        String summary,
        List<DemoRunSnapshotSectionVo> sections,
        List<String> packageEntries,
        List<DemoRunSnapshotLinkVo> links,
        List<String> exclusionPolicy,
        String markdown
) {
}
