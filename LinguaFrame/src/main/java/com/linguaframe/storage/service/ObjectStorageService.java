package com.linguaframe.storage.service;

import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;

import java.io.InputStream;

public interface ObjectStorageService {

    StoredObjectBo store(StoreObjectCommand command);

    InputStream open(String objectKey);
}
