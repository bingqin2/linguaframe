package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.vo.TranslationCacheHitVo;

import java.util.Optional;

public interface TranslationCacheService {

    Optional<TranslationCacheHitVo> findCachedTranslation(TranslationCacheLookupBo lookup);

    void storeTranslation(TranslationCacheLookupBo lookup, String jobId, TranslationResultBo result);
}
