package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;

public interface TranscriptionCacheKeyService {

    TranscriptionCacheLookupBo build(String provider, String model, String promptVersion, byte[] audioContent);
}
