package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationGlossaryEntryBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;

import java.util.List;

public interface TranslationProvider {

    default TranslationResultBo translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
        throw new UnsupportedOperationException("Translation provider must implement translate.");
    }

    default TranslationResultBo translate(
            String jobId,
            String targetLanguage,
            String translationStyle,
            List<TranscriptSegmentVo> transcriptSegments
    ) {
        return translate(jobId, targetLanguage, transcriptSegments);
    }

    default TranslationResultBo translate(
            String jobId,
            String targetLanguage,
            String translationStyle,
            List<TranslationGlossaryEntryBo> glossaryEntries,
            List<TranscriptSegmentVo> transcriptSegments
    ) {
        return translate(jobId, targetLanguage, translationStyle, transcriptSegments);
    }
}
