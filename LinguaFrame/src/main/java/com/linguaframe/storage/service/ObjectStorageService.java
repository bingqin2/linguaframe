package com.linguaframe.storage.service;

import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;

public interface ObjectStorageService {

    StoredObjectBo store(StoreObjectCommand command);
}
