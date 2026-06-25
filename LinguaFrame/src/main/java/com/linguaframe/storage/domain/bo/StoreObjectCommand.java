package com.linguaframe.storage.domain.bo;

import java.io.InputStream;

public record StoreObjectCommand(
        String objectKey,
        String contentType,
        long sizeBytes,
        InputStream inputStream
) {
}
