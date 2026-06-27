package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.vo.TranscriptionCacheHitVo;

import java.util.Optional;

public interface TranscriptionCacheService {

    Optional<TranscriptionCacheHitVo> findCachedTranscription(TranscriptionCacheLookupBo lookup);

    void storeTranscription(TranscriptionCacheLookupBo lookup, String jobId, TranscriptionResultBo result);
}
