package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.SubtitlePolishingCacheLookupBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;

import java.util.List;

public interface SubtitlePolishingCacheKeyService {

    SubtitlePolishingCacheLookupBo build(
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    );
}
