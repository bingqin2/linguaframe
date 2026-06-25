package com.linguaframe.storage.domain.bo;

public record StoredObjectBo(
        String bucket,
        String objectKey,
        long sizeBytes
) {
}
