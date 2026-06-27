package com.linguaframe.storage.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.storage.service.impl.MinioObjectStorageServiceImpl;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MinioObjectStorageServiceTests {

    @Test
    void deletesObjectFromConfiguredBucket() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        LinguaFrameProperties properties = properties();
        MinioObjectStorageServiceImpl service = new MinioObjectStorageServiceImpl(properties, minioClient);

        service.delete("objects/demo.mp4");

        verify(minioClient).removeObject(argThat((RemoveObjectArgs args) ->
                args.bucket().equals("linguaframe-artifacts") && args.object().equals("objects/demo.mp4")
        ));
    }

    @Test
    void wrapsObjectDeleteFailuresWithSafeMessage() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        LinguaFrameProperties properties = properties();
        MinioObjectStorageServiceImpl service = new MinioObjectStorageServiceImpl(properties, minioClient);
        doThrow(new RuntimeException("secret access key leaked"))
                .when(minioClient)
                .removeObject(org.mockito.ArgumentMatchers.any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> service.delete("objects/demo.mp4"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Object storage delete failed.")
                .hasRootCauseMessage("secret access key leaked");
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getStorage().setBucket("linguaframe-artifacts");
        return properties;
    }
}
