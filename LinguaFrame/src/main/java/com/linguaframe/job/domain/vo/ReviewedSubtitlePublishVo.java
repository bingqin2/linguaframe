package com.linguaframe.job.domain.vo;

import java.util.List;

public record ReviewedSubtitlePublishVo(
        String jobId,
        String targetLanguage,
        boolean burnedVideoRequested,
        boolean burnedVideoCreated,
        List<JobArtifactVo> artifacts
) {
}
