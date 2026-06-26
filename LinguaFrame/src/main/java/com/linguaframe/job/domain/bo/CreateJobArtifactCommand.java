package com.linguaframe.job.domain.bo;

import com.linguaframe.job.domain.enums.JobArtifactType;

public record CreateJobArtifactCommand(
        String jobId,
        JobArtifactType type,
        String filename,
        String contentType,
        byte[] content
) {
}
