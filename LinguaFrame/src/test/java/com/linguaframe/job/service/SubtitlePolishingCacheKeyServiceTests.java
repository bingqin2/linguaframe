package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.impl.SubtitlePolishingCacheKeyServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubtitlePolishingCacheKeyServiceTests {

    private final SubtitlePolishingCacheKeyService service = new SubtitlePolishingCacheKeyServiceImpl();

    @Test
    void returnsStableKeyForEquivalentNormalizedSubtitleInput() {
        var first = service.build(
                " zh-CN ",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-polishing-v1",
                " balanced ",
                List.of(
                        new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "  你好，世界。 "),
                        new SubtitleSegmentVo("zh-CN", 1, 1_000L, 2_000L, " LinguaFrame 演示 ")
                )
        );
        var second = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-polishing-v1",
                "BALANCED",
                List.of(
                        new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "你好，世界。"),
                        new SubtitleSegmentVo("zh-CN", 1, 1_000L, 2_000L, "LinguaFrame 演示")
                )
        );

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey());
        assertThat(first.sourceHash()).isEqualTo(second.sourceHash());
        assertThat(first.targetLanguage()).isEqualTo("zh-CN");
        assertThat(first.subtitlePolishingMode()).isEqualTo("BALANCED");
        assertThat(first.cacheKey()).matches("[a-f0-9]{64}");
        assertThat(first.sourceHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void changesKeyWhenCompatibilityInputsChange() {
        var baseline = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-test",
                "openai-subtitle-polishing-v1",
                "BALANCED",
                segments("你好。")
        );

        assertThat(service.build("en-US", "OPENAI", "gpt-test", "openai-subtitle-polishing-v1", "BALANCED", segments("你好。")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "DEMO", "gpt-test", "openai-subtitle-polishing-v1", "BALANCED", segments("你好。")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-next", "openai-subtitle-polishing-v1", "BALANCED", segments("你好。")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-test", "openai-subtitle-polishing-v2", "BALANCED", segments("你好。")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-test", "openai-subtitle-polishing-v1", "STRICT", segments("你好。")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-test", "openai-subtitle-polishing-v1", "BALANCED", segments("不同文本。")).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
    }

    @Test
    void rejectsBlankCompatibilityInputsAndEmptySegments() {
        assertThatThrownBy(() -> service.build(" ", "OPENAI", "gpt-test", "prompt-v1", "BALANCED", segments("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target language");
        assertThatThrownBy(() -> service.build("zh-CN", " ", "gpt-test", "prompt-v1", "BALANCED", segments("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", " ", "prompt-v1", "BALANCED", segments("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-test", " ", "BALANCED", segments("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt version");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-test", "prompt-v1", " ", segments("你好")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subtitle polishing mode");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-test", "prompt-v1", "BALANCED", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("segments");
    }

    private List<SubtitleSegmentVo> segments(String text) {
        return List.of(new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, text));
    }
}
