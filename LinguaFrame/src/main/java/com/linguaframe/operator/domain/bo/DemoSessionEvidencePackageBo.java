package com.linguaframe.operator.domain.bo;

import java.io.InputStream;

public record DemoSessionEvidencePackageBo(
        String filename,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}
