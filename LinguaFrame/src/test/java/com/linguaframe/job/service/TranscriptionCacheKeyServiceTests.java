package com.linguaframe.job.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranscriptionCacheKeyServiceTests {

    private final TranscriptionCacheKeyService service =
            new com.linguaframe.job.service.impl.TranscriptionCacheKeyServiceImpl();

    @Test
    void returnsStableKeyForEquivalentAudioInput() {
        var first = service.build(
                " OPENAI ",
                "whisper-1",
                "openai-audio-transcriptions-v1",
                new byte[] {1, 2, 3, 4}
        );
        var second = service.build(
                "OPENAI",
                "whisper-1",
                "openai-audio-transcriptions-v1",
                new byte[] {1, 2, 3, 4}
        );

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey());
        assertThat(first.audioHash()).isEqualTo(second.audioHash());
        assertThat(first.provider()).isEqualTo("OPENAI");
        assertThat(first.model()).isEqualTo("whisper-1");
        assertThat(first.promptVersion()).isEqualTo("openai-audio-transcriptions-v1");
        assertThat(first.cacheKey()).matches("[a-f0-9]{64}");
        assertThat(first.audioHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void changesKeyWhenCompatibilityInputsChange() {
        var baseline = service.build("OPENAI", "whisper-1", "openai-audio-transcriptions-v1", new byte[] {1, 2, 3});

        assertThat(service.build("DEMO", "whisper-1", "openai-audio-transcriptions-v1", new byte[] {1, 2, 3}).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("OPENAI", "gpt-4o-transcribe", "openai-audio-transcriptions-v1", new byte[] {1, 2, 3}).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("OPENAI", "whisper-1", "openai-audio-transcriptions-v2", new byte[] {1, 2, 3}).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("OPENAI", "whisper-1", "openai-audio-transcriptions-v1", new byte[] {1, 2, 4}).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
    }

    @Test
    void rejectsBlankCompatibilityInputsAndEmptyAudio() {
        assertThatThrownBy(() -> service.build(" ", "whisper-1", "prompt-v1", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
        assertThatThrownBy(() -> service.build("OPENAI", " ", "prompt-v1", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> service.build("OPENAI", "whisper-1", " ", new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt version");
        assertThatThrownBy(() -> service.build("OPENAI", "whisper-1", "prompt-v1", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audio");
    }
}
