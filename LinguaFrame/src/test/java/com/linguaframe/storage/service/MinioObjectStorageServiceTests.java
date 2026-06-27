package com.linguaframe.storage.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.storage.service.impl.MinioObjectStorageHealthCheckService;
import com.linguaframe.storage.service.impl.MinioObjectStorageServiceImpl;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void checksConfiguredBucketForHealthProbe() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        LinguaFrameProperties properties = properties();
        MinioObjectStorageHealthCheckService service = new MinioObjectStorageHealthCheckService(properties, minioClient);
        when(minioClient.bucketExists(org.mockito.ArgumentMatchers.any(BucketExistsArgs.class)))
                .thenReturn(true);

        assertThat(service.bucketExistsForHealthCheck()).isTrue();

        verify(minioClient).bucketExists(argThat((BucketExistsArgs args) ->
                args.bucket().equals("linguaframe-artifacts")
        ));
    }

    private LinguaFrameProperties properties() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getStorage().setBucket("linguaframe-artifacts");
        return properties;
    }
}
