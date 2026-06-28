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
}
