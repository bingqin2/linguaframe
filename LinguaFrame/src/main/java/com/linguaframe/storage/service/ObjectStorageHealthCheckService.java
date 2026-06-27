package com.linguaframe.storage.service;

public interface ObjectStorageHealthCheckService {

    boolean bucketExistsForHealthCheck() throws Exception;
}
