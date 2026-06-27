package com.linguaframe.job.service;

import java.math.BigDecimal;

public interface ModelCallSummaryService {

    String translationInput(String targetLanguage, int segmentCount, int sourceCharacterCount);

    String translationOutput(int segmentCount, int targetCharacterCount);

    String transcriptionInput(BigDecimal audioSeconds);

    String transcriptionOutput(int segmentCount, int textCharacterCount);

    String ttsInput(int characterCount);

    String ttsOutput(int audioByteCount);

    String evaluationInput(String targetLanguage, int sourceSegmentCount, int targetSegmentCount);

    String evaluationOutput(int score, String verdict);
}
