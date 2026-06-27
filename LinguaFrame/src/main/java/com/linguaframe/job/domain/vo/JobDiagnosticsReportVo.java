package com.linguaframe.job.domain.vo;

import java.time.Instant;
import java.util.List;

public record JobDiagnosticsReportVo(
        Instant generatedAt,
        LocalizationJobVo job,
        List<JobDiagnosticsArtifactVo> artifacts,
        int artifactCount
) {
}
