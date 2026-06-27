package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationCacheKeyServiceTests {

    private final TranslationCacheKeyService service = new com.linguaframe.job.service.impl.TranslationCacheKeyServiceImpl();

    @Test
    void returnsStableKeyForEquivalentNormalizedTranscriptInput() {
        var first = service.build(
                " zh-CN ",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1",
                List.of(
                        new TranscriptSegmentVo(0, 0L, 1_000L, " Hello world. "),
                        new TranscriptSegmentVo(1, 1_000L, 2_000L, "LinguaFrame demo")
                )
        );
        var second = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1",
                List.of(
                        new TranscriptSegmentVo(0, 0L, 1_000L, "Hello world."),
                        new TranscriptSegmentVo(1, 1_000L, 2_000L, " LinguaFrame demo ")
                )
        );

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey());
        assertThat(first.sourceHash()).isEqualTo(second.sourceHash());
        assertThat(first.targetLanguage()).isEqualTo("zh-CN");
        assertThat(first.cacheKey()).matches("[a-f0-9]{64}");
        assertThat(first.sourceHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void changesKeyWhenCompatibilityInputsChange() {
        var baseline = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-translation-v1",
                segments("Hello world.")
        );

        assertThat(service.build("en-US", "OPENAI", "gpt-test", "openai-subtitle-translation-v1", segments("Hello world.")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "DEMO", "gpt-test", "openai-subtitle-translation-v1", segments("Hello world.")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-next", "openai-subtitle-translation-v1", segments("Hello world.")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-test", "openai-subtitle-translation-v2", segments("Hello world.")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-test", "openai-subtitle-translation-v1", segments("Different text.")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
    }

    @Test
    void rejectsBlankCompatibilityInputsAndEmptySegments() {
        assertThatThrownBy(() -> service.build(" ", "OPENAI", "gpt-test", "prompt-v1", segments("Hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target language");
        assertThatThrownBy(() -> service.build("zh-CN", " ", "gpt-test", "prompt-v1", segments("Hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", " ", "prompt-v1", segments("Hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-test", " ", segments("Hello")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt version");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-test", "prompt-v1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segments");
    }

    private List<TranscriptSegmentVo> segments(String text) {
        return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, text));
    }
}
