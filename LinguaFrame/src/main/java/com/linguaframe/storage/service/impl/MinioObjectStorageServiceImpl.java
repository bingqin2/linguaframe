package com.linguaframe.storage.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class MinioObjectStorageServiceImpl implements ObjectStorageService {

    private final LinguaFrameProperties.Storage storageProperties;
    private final MinioClient minioClient;

    public MinioObjectStorageServiceImpl(LinguaFrameProperties properties) {
        this.storageProperties = properties.getStorage();
        this.minioClient = MinioClient.builder()
                .endpoint(storageProperties.getEndpoint())
                .credentials(storageProperties.getAccessKey(), storageProperties.getSecretKey())
                .build();
    }

    @Override
    public StoredObjectBo store(StoreObjectCommand command) {
        try {
            ensureBucketExists();
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .object(command.objectKey())
                    .contentType(command.contentType())
                    .stream(command.inputStream(), command.sizeBytes(), -1)
                    .build());
            return new StoredObjectBo(storageProperties.getBucket(), command.objectKey(), command.sizeBytes());
        } catch (Exception ex) {
            throw new IllegalStateException("Object storage write failed.", ex);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("Object storage read failed.", ex);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(storageProperties.getBucket())
                .build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder()
                    .bucket(storageProperties.getBucket())
                    .build());
        }
    }
}
