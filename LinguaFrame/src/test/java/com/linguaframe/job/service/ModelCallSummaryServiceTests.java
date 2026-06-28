package com.linguaframe.job.service;

import com.linguaframe.job.service.impl.ModelCallSummaryServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ModelCallSummaryServiceTests {

    private final ModelCallSummaryService service = new ModelCallSummaryServiceImpl();

    @Test
    void buildsTranslationSummariesFromCountsOnly() {
        String rawSourceText = "Hello from LinguaFrame.";
        String rawTargetText = "来自 LinguaFrame 的问候。";

        String inputSummary = service.translationInput("zh-CN", "FORMAL", 3, rawSourceText.length());
        String outputSummary = service.translationOutput(3, rawTargetText.length());

        assertThat(inputSummary).isEqualTo("target=zh-CN, style=FORMAL, segments=3, sourceChars=23");
        assertThat(outputSummary).isEqualTo("segments=3, targetChars=19");
        assertThat(inputSummary).doesNotContain(rawSourceText);
        assertThat(outputSummary).doesNotContain(rawTargetText);
    }

    @Test
    void buildsTranscriptionSummariesFromCountsOnly() {
        String rawTranscript = "This demo transcript is deterministic.";

        String inputSummary = service.transcriptionInput(new BigDecimal("45"));
        String outputSummary = service.transcriptionOutput(8, rawTranscript.length());

        assertThat(inputSummary).isEqualTo("audioSeconds=45.000");
        assertThat(outputSummary).isEqualTo("segments=8, transcriptChars=38");
        assertThat(outputSummary).doesNotContain(rawTranscript);
    }

    @Test
    void buildsTtsSummariesFromCountsOnly() {
        String rawTtsText = "Localized narration text.";

        String inputSummary = service.ttsInput(rawTtsText.length());
        String outputSummary = service.ttsOutput(34_567);

        assertThat(inputSummary).isEqualTo("characters=25");
        assertThat(outputSummary).isEqualTo("audioBytes=34567");
        assertThat(inputSummary).doesNotContain(rawTtsText);
    }

    @Test
    void buildsEvaluationSummariesFromCountsScoreAndVerdict() {
        assertThat(service.evaluationInput("zh-CN", 8, 8))
                .isEqualTo("target=zh-CN, sourceSegments=8, targetSegments=8");
        assertThat(service.evaluationOutput(88, "Good"))
                .isEqualTo("score=88, verdict=Good");
    }

    @Test
    void normalizesBlankEvaluationVerdict() {
        assertThat(service.evaluationOutput(0, " "))
                .isEqualTo("score=0, verdict=Unavailable");
    }
}
