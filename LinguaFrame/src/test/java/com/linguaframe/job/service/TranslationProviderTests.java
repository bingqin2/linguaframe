package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.DemoTranslationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TranslationProviderTests {

    private final DemoTranslationProvider provider = new DemoTranslationProvider();

    @Test
    void demoProviderReturnsDeterministicChineseTextAndPreservesTiming() {
        var result = provider.translate("translation-job-1", "zh-CN", List.of(
                new TranscriptSegmentVo(0, 0L, 1_800L, "Hello from LinguaFrame."),
                new TranscriptSegmentVo(1, 1_800L, 3_600L, "This demo transcript is deterministic.")
        ));

        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.startMs() + ":" + segment.endMs() + ":" + segment.text())
                .containsExactly(
                        "0:0:1800:LinguaFrame 向你问好。",
                        "1:1800:3600:这个演示字幕是确定性的。"
                );
    }

    @Test
    void demoProviderPrefixesUnknownTextWithTargetLanguage() {
        var result = provider.translate("translation-job-2", "fr-FR", List.of(
                new TranscriptSegmentVo(0, 0L, 1_000L, "Unknown source text.")
        ));

        assertThat(result.segments().getFirst().text()).isEqualTo("[fr-FR] Unknown source text.");
    }
}
