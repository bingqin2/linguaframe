package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;

import java.util.List;

public interface TranslationProvider {

    TranslationResultBo translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments);
}
