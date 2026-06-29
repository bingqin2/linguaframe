package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationVoicePresetVo;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class NarrationVoiceCatalogServiceImpl implements NarrationVoiceCatalogService {

    private static final String DEMO_PROVIDER = "demo";
    private static final String OPENAI_PROVIDER = "openai";
    private static final String DEMO_DEFAULT_VOICE = "demo-voice";
    private static final String OPENAI_DEFAULT_VOICE = "alloy";
    private static final List<String> OPENAI_VOICES = List.of(
            "alloy",
            "ash",
            "ballad",
            "coral",
            "echo",
            "fable",
            "nova",
            "onyx",
            "sage",
            "shimmer",
            "verse"
    );

    private final LinguaFrameProperties properties;

    public NarrationVoiceCatalogServiceImpl(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public NarrationVoiceCatalogVo catalog() {
        String provider = provider();
        String defaultVoice = defaultVoice(provider);
        List<NarrationVoicePresetVo> presets = OPENAI_PROVIDER.equals(provider)
                ? openAiPresets(defaultVoice)
                : demoPresets(defaultVoice);
        return new NarrationVoiceCatalogVo(
                provider,
                defaultVoice,
                presets,
                List.of(
                        "Voice presets are provider identifiers, not uploaded reference audio.",
                        "Blank segment voice values inherit the job or provider default voice."
                )
        );
    }

    private String provider() {
        String provider = properties.getTts().getProvider();
        if (provider == null || provider.isBlank()) {
            return DEMO_PROVIDER;
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultVoice(String provider) {
        if (OPENAI_PROVIDER.equals(provider)) {
            String configured = properties.getTts().getOpenai().getVoice();
            return configured == null || configured.isBlank() ? OPENAI_DEFAULT_VOICE : configured.trim();
        }
        return DEMO_DEFAULT_VOICE;
    }

    private List<NarrationVoicePresetVo> demoPresets(String defaultVoice) {
        return List.of(new NarrationVoicePresetVo(
                DEMO_DEFAULT_VOICE,
                "Demo voice",
                DEMO_PROVIDER,
                DEMO_DEFAULT_VOICE.equals(defaultVoice),
                "Deterministic local demo TTS voice."
        ));
    }

    private List<NarrationVoicePresetVo> openAiPresets(String defaultVoice) {
        return OPENAI_VOICES.stream()
                .map(voice -> new NarrationVoicePresetVo(
                        voice,
                        label(voice),
                        OPENAI_PROVIDER,
                        voice.equals(defaultVoice),
                        "OpenAI TTS voice preset."
                ))
                .toList();
    }

    private String label(String voice) {
        return voice.substring(0, 1).toUpperCase(Locale.ROOT) + voice.substring(1);
    }
}
