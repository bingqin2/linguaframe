package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TtsCacheLookupBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.vo.TtsCacheHitVo;

import java.util.Optional;

public interface TtsCacheService {

    Optional<TtsCacheHitVo> findCachedTts(TtsCacheLookupBo lookup);

    void storeTts(TtsCacheLookupBo lookup, String jobId, TtsResultBo result);
}
