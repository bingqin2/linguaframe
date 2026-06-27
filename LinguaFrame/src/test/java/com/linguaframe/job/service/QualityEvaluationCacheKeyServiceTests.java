package com.linguaframe.job.service;

import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QualityEvaluationCacheKeyServiceTests {

    private final QualityEvaluationCacheKeyService service =
            new com.linguaframe.job.service.impl.QualityEvaluationCacheKeyServiceImpl();

    @Test
    void returnsStableKeyForEquivalentEvaluationInput() {
        var first = service.build(
                " zh-CN ",
                " OPENAI ",
                "gpt-4o-mini",
                "quality-evaluation-v1",
                sourceSegments(),
                targetSegments()
        );
        var second = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini",
                "quality-evaluation-v1",
                sourceSegments(),
                targetSegments()
        );

        assertThat(first.cacheKey()).isEqualTo(second.cacheKey());
        assertThat(first.sourceHash()).isEqualTo(second.sourceHash());
        assertThat(first.targetHash()).isEqualTo(second.targetHash());
        assertThat(first.language()).isEqualTo("zh-CN");
        assertThat(first.provider()).isEqualTo("OPENAI");
        assertThat(first.model()).isEqualTo("gpt-4o-mini");
        assertThat(first.promptVersion()).isEqualTo("quality-evaluation-v1");
        assertThat(first.cacheKey()).matches("[a-f0-9]{64}");
        assertThat(first.sourceHash()).matches("[a-f0-9]{64}");
        assertThat(first.targetHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void changesKeyWhenCompatibilityInputsChange() {
        var baseline = service.build(
                "zh-CN",
                "OPENAI",
                "gpt-4o-mini",
                "quality-evaluation-v1",
                sourceSegments(),
                targetSegments()
        );

        assertThat(service.build("ja-JP", "OPENAI", "gpt-4o-mini", "quality-evaluation-v1", sourceSegments(), targetSegments()).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "DEMO", "gpt-4o-mini", "quality-evaluation-v1", sourceSegments(), targetSegments()).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-4.1-mini", "quality-evaluation-v1", sourceSegments(), targetSegments()).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-4o-mini", "quality-evaluation-v2", sourceSegments(), targetSegments()).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-4o-mini", "quality-evaluation-v1", alternateSourceSegments(), targetSegments()).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
        assertThat(service.build("zh-CN", "OPENAI", "gpt-4o-mini", "quality-evaluation-v1", sourceSegments(), alternateTargetSegments()).cacheKey())
                .isNotEqualTo(baseline.cacheKey());
    }

    @Test
    void rejectsBlankCompatibilityInputsAndEmptySegments() {
        assertThatThrownBy(() -> service.build(" ", "OPENAI", "model", "prompt-v1", sourceSegments(), targetSegments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");
        assertThatThrownBy(() -> service.build("zh-CN", " ", "model", "prompt-v1", sourceSegments(), targetSegments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", " ", "prompt-v1", sourceSegments(), targetSegments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "model", " ", sourceSegments(), targetSegments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prompt version");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "model", "prompt-v1", List.of(), targetSegments()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source segments");
        assertThatThrownBy(() -> service.build("zh-CN", "OPENAI", "model", "prompt-v1", sourceSegments(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target segments");
    }

    private List<TranscriptSegmentVo> sourceSegments() {
        return List.of(
                new TranscriptSegmentVo(0, 0L, 1_000L, "Hello"),
                new TranscriptSegmentVo(1, 1_000L, 2_000L, "World")
        );
    }

    private List<TranscriptSegmentVo> alternateSourceSegments() {
        return List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Different"));
    }

    private List<SubtitleSegmentVo> targetSegments() {
        return List.of(
                new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "你好"),
                new SubtitleSegmentVo("zh-CN", 1, 1_000L, 2_000L, "世界")
        );
    }

    private List<SubtitleSegmentVo> alternateTargetSegments() {
        return List.of(new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "不同"));
    }
}
