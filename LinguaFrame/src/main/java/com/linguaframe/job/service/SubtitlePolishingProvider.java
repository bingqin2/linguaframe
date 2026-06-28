package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;

import java.util.List;

public interface SubtitlePolishingProvider {

    SubtitlePolishingResultBo polish(
            String jobId,
            String targetLanguage,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    );
}
