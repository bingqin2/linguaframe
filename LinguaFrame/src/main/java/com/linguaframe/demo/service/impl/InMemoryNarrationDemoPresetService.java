package com.linguaframe.demo.service.impl;

import com.linguaframe.demo.domain.vo.NarrationDemoPresetMixSettingsVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetSegmentVo;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.service.NarrationDemoPresetService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InMemoryNarrationDemoPresetService implements NarrationDemoPresetService {

    private static final List<NarrationDemoPresetVo> PRESETS = List.of(
            new NarrationDemoPresetVo(
                    "tears-showcase-narration",
                    "Tears showcase narration",
                    "Reusable explanatory narration for the Tears of Steel demo run.",
                    "tears-showcase",
                    "tears-of-steel-casting",
                    "zh-CN",
                    "PRESET:alloy",
                    4,
                    0,
                    new BigDecimal("113.000"),
                    new NarrationDemoPresetMixSettingsVo(
                            new BigDecimal("0.350"),
                            new BigDecimal("1.000"),
                            250
                    ),
                    segments(),
                    List.of(
                            "Preset text is operator-authored narration intended for workspace restoration.",
                            "Safe summaries should expose counts, timings, and preset identifiers only.",
                            "Apply the preset first, then generate narration audio or narrated video explicitly."
                    )
            )
    ).stream()
            .map(InMemoryNarrationDemoPresetService::withComputedTotals)
            .toList();

    @Override
    public List<NarrationDemoPresetVo> listPresets() {
        return PRESETS;
    }

    @Override
    public Optional<NarrationDemoPresetVo> findByProfileId(String profileId) {
        String normalized = normalize(profileId);
        if (normalized == null) {
            return Optional.empty();
        }
        return PRESETS.stream()
                .filter(preset -> preset.profileId().equals(normalized))
                .findFirst();
    }

    @Override
    public Optional<NarrationDemoPresetVo> findById(String presetId) {
        String normalized = normalize(presetId);
        if (normalized == null) {
            return Optional.empty();
        }
        return PRESETS.stream()
                .filter(preset -> preset.id().equals(normalized))
                .findFirst();
    }

    private static List<NarrationDemoPresetSegmentVo> segments() {
        return List.of(
                segment(
                        0,
                        "15.000",
                        "28.000",
                        "这段演示先展示源视频和对白场景，LinguaFrame 会在后台完成语音识别、翻译和字幕生成。",
                        "alloy"
                ),
                segment(
                        1,
                        "55.000",
                        "70.000",
                        "这里适合强调术语表和字幕风格预设如何保持角色名、片名和关键技术词的一致性。",
                        "alloy"
                ),
                segment(
                        2,
                        "118.000",
                        "136.000",
                        "处理完成后，可以检查质量评分、模型调用、成本和缓存证据，而不需要打开后端日志。",
                        "alloy"
                ),
                segment(
                        3,
                        "162.000",
                        "180.000",
                        "最后生成单独的讲解音轨和讲解版视频，用于展示人工补充说明如何叠加在本地化结果上。",
                        "alloy"
                )
        );
    }

    private static NarrationDemoPresetSegmentVo segment(
            int index,
            String startSeconds,
            String endSeconds,
            String text,
            String voice
    ) {
        BigDecimal start = new BigDecimal(startSeconds);
        BigDecimal end = new BigDecimal(endSeconds);
        return new NarrationDemoPresetSegmentVo(
                index,
                start,
                end,
                end.subtract(start),
                text,
                text.length(),
                voice
        );
    }

    private static NarrationDemoPresetVo withComputedTotals(NarrationDemoPresetVo preset) {
        int characterCount = preset.segments().stream()
                .mapToInt(NarrationDemoPresetSegmentVo::characterCount)
                .sum();
        BigDecimal timeSpan = preset.segments().isEmpty()
                ? BigDecimal.ZERO
                : preset.segments().get(preset.segments().size() - 1).endSeconds()
                .subtract(preset.segments().get(0).startSeconds());
        return new NarrationDemoPresetVo(
                preset.id(),
                preset.label(),
                preset.description(),
                preset.profileId(),
                preset.sampleIdHint(),
                preset.targetLanguage(),
                preset.voiceSummary(),
                preset.segments().size(),
                characterCount,
                timeSpan,
                preset.mixSettings(),
                preset.segments(),
                preset.safetyNotes()
        );
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
