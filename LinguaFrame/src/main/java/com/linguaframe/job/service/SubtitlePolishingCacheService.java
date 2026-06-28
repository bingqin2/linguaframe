package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.SubtitlePolishingCacheLookupBo;
import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.vo.SubtitlePolishingCacheHitVo;

import java.util.Optional;

public interface SubtitlePolishingCacheService {

    Optional<SubtitlePolishingCacheHitVo> findCachedPolishing(SubtitlePolishingCacheLookupBo lookup);

    void storePolishing(SubtitlePolishingCacheLookupBo lookup, String jobId, SubtitlePolishingResultBo result);
}
