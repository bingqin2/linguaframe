package com.linguaframe.job.service.impl;

import com.linguaframe.job.service.ModelCallSummaryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ModelCallSummaryServiceImpl implements ModelCallSummaryService {

    @Override
    public String translationInput(String targetLanguage, int segmentCount, int sourceCharacterCount) {
        return "target=%s, segments=%d, sourceChars=%d".formatted(
                safeValue(targetLanguage),
                segmentCount,
                sourceCharacterCount
        );
    }

    @Override
    public String translationOutput(int segmentCount, int targetCharacterCount) {
        return "segments=%d, targetChars=%d".formatted(segmentCount, targetCharacterCount);
    }

    @Override
    public String transcriptionInput(BigDecimal audioSeconds) {
        return "audioSeconds=%s".formatted(formatSeconds(audioSeconds));
    }

    @Override
    public String transcriptionOutput(int segmentCount, int textCharacterCount) {
        return "segments=%d, transcriptChars=%d".formatted(segmentCount, textCharacterCount);
    }

    @Override
    public String ttsInput(int characterCount) {
        return "characters=%d".formatted(characterCount);
    }

    @Override
    public String ttsOutput(int audioByteCount) {
        return "audioBytes=%d".formatted(audioByteCount);
    }

    @Override
    public String evaluationInput(String targetLanguage, int sourceSegmentCount, int targetSegmentCount) {
        return "target=%s, sourceSegments=%d, targetSegments=%d".formatted(
                safeValue(targetLanguage),
                sourceSegmentCount,
                targetSegmentCount
        );
    }

    @Override
    public String evaluationOutput(int score, String verdict) {
        return "score=%d, verdict=%s".formatted(score, safeValue(verdict));
    }

    private String formatSeconds(BigDecimal value) {
        if (value == null) {
            return "0.000";
        }
        return value.setScale(3, RoundingMode.HALF_UP).toPlainString();
    }

    private String safeValue(String value) {
        if (value == null || value.isBlank()) {
            return "Unavailable";
        }
        return value.trim();
    }
}
