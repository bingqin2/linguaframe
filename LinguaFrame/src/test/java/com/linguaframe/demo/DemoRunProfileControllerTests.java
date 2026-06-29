package com.linguaframe.demo;

import com.linguaframe.LinguaFrameApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LinguaFrameApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoRunProfileControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsBuiltInDemoRunProfiles() throws Exception {
        mockMvc.perform(get("/api/demo-run-profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("quick-baseline"))
                .andExpect(jsonPath("$[0].targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$[0].translationStyle").value("NATURAL"))
                .andExpect(jsonPath("$[0].subtitleStylePreset").value("STANDARD"))
                .andExpect(jsonPath("$[0].subtitlePolishingMode").value("OFF"))
                .andExpect(jsonPath("$[1].id").value("tears-showcase"))
                .andExpect(jsonPath("$[1].subtitleStylePreset").value("HIGH_CONTRAST"))
                .andExpect(jsonPath("$[1].subtitlePolishingMode").value("BALANCED"))
                .andExpect(jsonPath("$[1].translationGlossary").value(org.hamcrest.Matchers.containsString("Tears of Steel")))
                .andExpect(jsonPath("$[2].id").value("concise-review"))
                .andExpect(jsonPath("$[2].translationStyle").value("CONCISE"))
                .andExpect(jsonPath("$[2].subtitleStylePreset").value("LARGE"))
                .andExpect(jsonPath("$[2].subtitlePolishingMode").value("STRICT"));
    }

    @Test
    void listsNarrationDemoPresets() throws Exception {
        mockMvc.perform(get("/api/demo-run-profiles/narration-presets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("tears-showcase-narration"))
                .andExpect(jsonPath("$[0].profileId").value("tears-showcase"))
                .andExpect(jsonPath("$[0].sampleIdHint").value("tears-of-steel-casting"))
                .andExpect(jsonPath("$[0].segmentCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$[0].mixSettings.duckingVolume").value(0.35))
                .andExpect(jsonPath("$[0].segments[0].text").isNotEmpty());
    }

    @Test
    void getsNarrationPresetByDemoProfile() throws Exception {
        mockMvc.perform(get("/api/demo-run-profiles/tears-showcase/narration-preset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("tears-showcase-narration"))
                .andExpect(jsonPath("$.profileId").value("tears-showcase"))
                .andExpect(jsonPath("$.voiceSummary").value("DEFAULT"));
    }

    @Test
    void returnsNoContentWhenProfileHasNoNarrationPreset() throws Exception {
        mockMvc.perform(get("/api/demo-run-profiles/quick-baseline/narration-preset"))
                .andExpect(status().isNoContent());
    }
}
