package com.linguaframe.storage.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.storage.service.ObjectStorageHealthCheckService;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MinioObjectStorageHealthCheckService implements ObjectStorageHealthCheckService {

    private final LinguaFrameProperties.Storage storageProperties;
    private final MinioClient minioClient;

    @Autowired
    public MinioObjectStorageHealthCheckService(LinguaFrameProperties properties) {
        this.storageProperties = properties.getStorage();
        this.minioClient = MinioClient.builder()
                .endpoint(storageProperties.getEndpoint())
                .credentials(storageProperties.getAccessKey(), storageProperties.getSecretKey())
                .build();
    }

    public MinioObjectStorageHealthCheckService(LinguaFrameProperties properties, MinioClient minioClient) {
        this.storageProperties = properties.getStorage();
        this.minioClient = minioClient;
    }

    @Override
    public boolean bucketExistsForHealthCheck() throws Exception {
        return minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(storageProperties.getBucket())
                .build());
    }
}
