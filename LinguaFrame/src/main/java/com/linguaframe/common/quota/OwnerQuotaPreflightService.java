package com.linguaframe.common.quota;

public interface OwnerQuotaPreflightService {

    OwnerQuotaPreflightVo getPreflight();

    void requireUploadAllowed();
}
