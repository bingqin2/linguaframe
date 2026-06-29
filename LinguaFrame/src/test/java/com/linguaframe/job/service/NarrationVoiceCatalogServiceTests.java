package com.linguaframe.job.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.service.impl.NarrationVoiceCatalogServiceImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationVoiceCatalogServiceTests {

    @Test
    void returnsDemoProviderVoiceCatalog() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTts().setProvider("demo");

        NarrationVoiceCatalogService service = new NarrationVoiceCatalogServiceImpl(properties);

        NarrationVoiceCatalogVo catalog = service.catalog();

        assertThat(catalog.provider()).isEqualTo("demo");
        assertThat(catalog.defaultVoice()).isEqualTo("demo-voice");
        assertThat(catalog.presets())
                .extracting(preset -> preset.voice() + ":" + preset.label() + ":" + preset.provider() + ":" + preset.defaultPreset())
                .containsExactly("demo-voice:Demo voice:demo:true");
        assertThat(catalog.safetyNotes())
                .contains("Voice presets are provider identifiers, not uploaded reference audio.");
    }

    @Test
    void returnsOpenAiCatalogWithConfiguredDefaultVoice() {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getTts().setProvider("openai");
        properties.getTts().getOpenai().setVoice("verse");

        NarrationVoiceCatalogService service = new NarrationVoiceCatalogServiceImpl(properties);

        NarrationVoiceCatalogVo catalog = service.catalog();

        assertThat(catalog.provider()).isEqualTo("openai");
        assertThat(catalog.defaultVoice()).isEqualTo("verse");
        assertThat(catalog.presets())
                .extracting("voice")
                .contains("alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer", "verse");
        assertThat(catalog.presets())
                .filteredOn("voice", "verse")
                .singleElement()
                .extracting("defaultPreset")
                .isEqualTo(true);
    }
}
