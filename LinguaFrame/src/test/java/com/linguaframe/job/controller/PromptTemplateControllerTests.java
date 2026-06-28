package com.linguaframe.job.controller;

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
class PromptTemplateControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsActivePromptTemplates() throws Exception {
        mockMvc.perform(get("/api/prompt-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].purpose").value("SUBTITLE_TRANSLATION"))
                .andExpect(jsonPath("$[0].version").value("openai-subtitle-translation-v1"))
                .andExpect(jsonPath("$[0].provider").value("OPENAI"))
                .andExpect(jsonPath("$[0].modelFamily").value("responses"))
                .andExpect(jsonPath("$[0].systemPrompt").isNotEmpty())
                .andExpect(jsonPath("$[0].outputContract").value("Return JSON with segments[{index,text}] preserving order and timing."))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[1].purpose").value("SUBTITLE_POLISHING"))
                .andExpect(jsonPath("$[1].version").value("openai-subtitle-polishing-v1"))
                .andExpect(jsonPath("$[1].outputContract").value("Return JSON with segments[{index,text}] and do not add or remove segments."))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[2].purpose").value("TRANSLATION_QUALITY_EVALUATION"))
                .andExpect(jsonPath("$[2].version").value("openai-translation-quality-evaluation-v1"))
                .andExpect(jsonPath("$[2].outputContract").value("Return JSON with score, verdict, completeness, readability, timingPreservation, naturalness, issues, and suggestedFixes."))
                .andExpect(jsonPath("$[2].active").value(true));
    }
}
