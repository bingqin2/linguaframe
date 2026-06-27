package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TtsCacheLookupBo;

public interface TtsCacheKeyService {

    TtsCacheLookupBo build(String language, String provider, String model, String voice, String text);
}
