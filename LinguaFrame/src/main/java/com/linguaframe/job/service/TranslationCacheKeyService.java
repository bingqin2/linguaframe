package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;

import java.util.List;

public interface TranslationCacheKeyService {

    default TranslationCacheLookupBo build(
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            List<TranscriptSegmentVo> segments
    ) {
        return build(targetLanguage, provider, model, promptVersion, "NATURAL", segments);
    }

    default TranslationCacheLookupBo build(
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            String translationStyle,
            List<TranscriptSegmentVo> segments
    ) {
        return build(targetLanguage, provider, model, promptVersion, segments);
    }
}
