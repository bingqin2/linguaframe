package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.service.PromptTemplateRegistry;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class InMemoryPromptTemplateRegistry implements PromptTemplateRegistry {

    private static final PromptTemplateVo SUBTITLE_TRANSLATION_TEMPLATE = new PromptTemplateVo(
            "openai-subtitle-translation-v1",
            PromptTemplatePurpose.SUBTITLE_TRANSLATION,
            "OPENAI",
            "responses",
            "You translate subtitle segments for video localization. Translate text only, preserve the meaning and line order, and return JSON only.",
            "Return JSON with segments[{index,text}] preserving order and timing.",
            true
    );

    private static final PromptTemplateVo TRANSLATION_QUALITY_EVALUATION_TEMPLATE = new PromptTemplateVo(
            "openai-translation-quality-evaluation-v1",
            PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION,
            "OPENAI",
            "responses",
            "You evaluate translated subtitle quality for video localization. Return JSON only with numeric scores from 0 to 100.",
            "Return JSON with score, verdict, completeness, readability, timingPreservation, naturalness, issues, and suggestedFixes.",
            true
    );

    private static final PromptTemplateVo SUBTITLE_POLISHING_TEMPLATE = new PromptTemplateVo(
            "openai-subtitle-polishing-v1",
            PromptTemplatePurpose.SUBTITLE_POLISHING,
            "OPENAI",
            "responses",
            "You polish translated subtitle segments for video localization. Preserve meaning, timing, and segment order. Return JSON only.",
            "Return JSON with segments[{index,text}] and do not add or remove segments.",
            true
    );

    private final Map<PromptTemplatePurpose, PromptTemplateVo> activeTemplates;

    public InMemoryPromptTemplateRegistry() {
        EnumMap<PromptTemplatePurpose, PromptTemplateVo> templates = new EnumMap<>(PromptTemplatePurpose.class);
        templates.put(SUBTITLE_TRANSLATION_TEMPLATE.purpose(), SUBTITLE_TRANSLATION_TEMPLATE);
        templates.put(SUBTITLE_POLISHING_TEMPLATE.purpose(), SUBTITLE_POLISHING_TEMPLATE);
        templates.put(TRANSLATION_QUALITY_EVALUATION_TEMPLATE.purpose(), TRANSLATION_QUALITY_EVALUATION_TEMPLATE);
        this.activeTemplates = Map.copyOf(templates);
    }

    @Override
    public PromptTemplateVo activeTemplate(PromptTemplatePurpose purpose) {
        PromptTemplateVo template = activeTemplates.get(purpose);
        if (template == null) {
            throw new NoSuchElementException("Active prompt template not found for purpose " + purpose + ".");
        }
        return template;
    }

    @Override
    public List<PromptTemplateVo> listActiveTemplates() {
        return List.of(
                activeTemplate(PromptTemplatePurpose.SUBTITLE_TRANSLATION),
                activeTemplate(PromptTemplatePurpose.SUBTITLE_POLISHING),
                activeTemplate(PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION)
        );
    }
}
