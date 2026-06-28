package com.linguaframe.job.service;

import java.math.BigDecimal;

public interface ModelCallSummaryService {

    default String translationInput(String targetLanguage, int segmentCount, int sourceCharacterCount) {
        return translationInput(targetLanguage, "NATURAL", segmentCount, sourceCharacterCount);
    }

    String translationInput(String targetLanguage, String translationStyle, int segmentCount, int sourceCharacterCount);

    default String translationInput(
            String targetLanguage,
            String translationStyle,
            int segmentCount,
            int sourceCharacterCount,
            int glossaryEntryCount
    ) {
        return translationInput(targetLanguage, translationStyle, segmentCount, sourceCharacterCount);
    }

    String translationOutput(int segmentCount, int targetCharacterCount);

    String transcriptionInput(BigDecimal audioSeconds);

    String transcriptionOutput(int segmentCount, int textCharacterCount);

    String ttsInput(int characterCount);

    String ttsOutput(int audioByteCount);

    String evaluationInput(String targetLanguage, int sourceSegmentCount, int targetSegmentCount);

    String evaluationOutput(int score, String verdict);
}
