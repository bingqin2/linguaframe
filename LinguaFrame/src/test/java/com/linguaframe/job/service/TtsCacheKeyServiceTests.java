package com.linguaframe.job.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtsCacheKeyServiceTests {

    private final TtsCacheKeyService service = new com.linguaframe.job.service.impl.TtsCacheKeyServiceImpl();

    @Test
    void returnsStableKeyForEquivalentNormalizedTtsText() {
        var first = service.build(
                " zh-CN ",
                "OPENAI",
                "gpt-4o-mini-tts",
                "alloy",
                " 第一行字幕 \n\n 第二行字幕 "
        );
        var second = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini-tts",
                "alloy",
                "第一行字幕\n第二行字幕"
        );

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey());
        assertThat(first.textHash()).isEqualTo(second.textHash());
        assertThat(first.language()).isEqualTo("zh-CN");
        assertThat(first.provider()).isEqualTo("OPENAI");
        assertThat(first.model()).isEqualTo("gpt-4o-mini-tts");
        assertThat(first.voice()).isEqualTo("alloy");
        assertThat(first.cacheKey()).matches("[a-f0-9]{64}");
        assertThat(first.textHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void changesKeyWhenCompatibilityInputsChange() {
        var baseline = service.build("zh-CN", "OPENAI", "gpt-4o-mini-tts", "alloy", "你好世界");

        assertThat(service.build("en-US", "OPENAI", "gpt-4o-mini-tts", "alloy", "你好世界").cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "DEMO", "gpt-4o-mini-tts", "alloy", "你好世界").cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "tts-next", "alloy", "你好世界").cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-4o-mini-tts", "verse", "你好世界").cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-4o-mini-tts", "alloy", "不同文本").cacheKey())
                .isNotEqualTo(baseline.cacheKey());
    }

    @Test
    void rejectsBlankCompatibilityInputsAndText() {
        assertThatThrownBy(() -> service.build(" ", "OPENAI", "gpt-4o-mini-tts", "alloy", "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
        assertThatThrownBy(() -> service.build("zh-CN", " ", "gpt-4o-mini-tts", "alloy", "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", " ", "alloy", "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-4o-mini-tts", " ", "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("voice");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "gpt-4o-mini-tts", "alloy", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }
}
