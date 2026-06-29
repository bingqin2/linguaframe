package com.linguaframe.job.domain.bo;

import java.io.InputStream;

public record StoredDemoEvidenceClosurePackageBo(
        String filename,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}
