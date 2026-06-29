package com.linguaframe.demo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.demo.domain.vo.NarrationDemoPresetVo;
import com.linguaframe.demo.service.impl.InMemoryNarrationDemoPresetService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationDemoPresetServiceTests {

    private final NarrationDemoPresetService service = new InMemoryNarrationDemoPresetService();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void listsBuiltInNarrationPresetForTearsShowcase() {
        NarrationDemoPresetVo preset = service.listPresets().stream()
                .filter(candidate -> candidate.id().equals("tears-showcase-narration"))
                .findFirst()
                .orElseThrow();

        assertThat(preset.profileId()).isEqualTo("tears-showcase");
        assertThat(preset.sampleIdHint()).isEqualTo("tears-of-steel-casting");
        assertThat(preset.targetLanguage()).isEqualTo("zh-CN");
        assertThat(preset.segmentCount()).isBetween(3, 5);
        assertThat(preset.totalCharacterCount()).isGreaterThan(80);
        assertThat(preset.timeSpanSeconds()).isGreaterThan(BigDecimal.ZERO);
        assertThat(preset.mixSettings().duckingVolume()).isEqualByComparingTo("0.350");
        assertThat(preset.voiceSummary()).isEqualTo("DEFAULT");
        assertThat(preset.safetyNotes())
                .anySatisfy(note -> assertThat(note).contains("operator-authored"));
    }

    @Test
    void findsPresetByProfileIdAndNormalizesProfileCase() {
        assertThat(service.findByProfileId(" TEARS-SHOWCASE "))
                .hasValueSatisfying(preset -> {
                    assertThat(preset.id()).isEqualTo("tears-showcase-narration");
                    assertThat(preset.profileId()).isEqualTo("tears-showcase");
                });
    }

    @Test
    void returnsEmptyForProfileWithoutNarrationPreset() {
        assertThat(service.findByProfileId("quick-baseline")).isEmpty();
        assertThat(service.findByProfileId("unknown-profile")).isEmpty();
        assertThat(service.findByProfileId(" ")).isEmpty();
    }

    @Test
    void presetSegmentsAreOrderedAndWithinFiveMinuteUploadLimit() {
        NarrationDemoPresetVo preset = service.findByProfileId("tears-showcase").orElseThrow();

        assertThat(preset.segments())
                .isSortedAccordingTo((left, right) -> left.startSeconds().compareTo(right.startSeconds()));
        assertThat(preset.segments())
                .allSatisfy(segment -> {
                    assertThat(segment.endSeconds()).isGreaterThan(segment.startSeconds());
                    assertThat(segment.endSeconds()).isLessThanOrEqualTo(new BigDecimal("300.000"));
                    assertThat(segment.text()).isNotBlank();
                    assertThat(segment.characterCount()).isEqualTo(segment.text().length());
                });
    }

    @Test
    void presetCatalogDoesNotExposeUnsafeRuntimeValues() throws Exception {
        String json = objectMapper.writeValueAsString(service.listPresets());

        assertThat(json)
                .doesNotContain("/Users/")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("objectKey")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("sk-")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }
}
