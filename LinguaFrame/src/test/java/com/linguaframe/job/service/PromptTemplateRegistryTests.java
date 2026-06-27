package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.service.impl.InMemoryPromptTemplateRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateRegistryTests {

    private final PromptTemplateRegistry registry = new InMemoryPromptTemplateRegistry();

    @Test
    void returnsActiveTranslationTemplate() {
        var template = registry.activeTemplate(PromptTemplatePurpose.SUBTITLE_TRANSLATION);

        assertThat(template.version()).isEqualTo("openai-subtitle-translation-v1");
        assertThat(template.purpose()).isEqualTo(PromptTemplatePurpose.SUBTITLE_TRANSLATION);
        assertThat(template.provider()).isEqualTo("OPENAI");
        assertThat(template.modelFamily()).isEqualTo("responses");
        assertThat(template.systemPrompt()).contains("translate subtitle segments");
        assertThat(template.outputContract())
                .isEqualTo("Return JSON with segments[{index,text}] preserving order and timing.");
        assertThat(template.active()).isTrue();
    }

    @Test
    void returnsActiveQualityEvaluationTemplate() {
        var template = registry.activeTemplate(PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION);

        assertThat(template.version()).isEqualTo("openai-translation-quality-evaluation-v1");
        assertThat(template.purpose()).isEqualTo(PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION);
        assertThat(template.provider()).isEqualTo("OPENAI");
        assertThat(template.modelFamily()).isEqualTo("responses");
        assertThat(template.systemPrompt()).contains("evaluate translated subtitle quality");
        assertThat(template.outputContract())
                .isEqualTo("Return JSON with score, verdict, completeness, readability, timingPreservation, naturalness, issues, and suggestedFixes.");
        assertThat(template.active()).isTrue();
    }

    @Test
    void listsOnlyActiveTemplates() {
        assertThat(registry.listActiveTemplates())
                .extracting(template -> template.purpose() + ":" + template.version())
                .containsExactly(
                        "SUBTITLE_TRANSLATION:openai-subtitle-translation-v1",
                        "TRANSLATION_QUALITY_EVALUATION:openai-translation-quality-evaluation-v1"
                );
    }
}
