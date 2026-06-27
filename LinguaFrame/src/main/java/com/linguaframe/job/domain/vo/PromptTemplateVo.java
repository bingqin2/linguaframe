package com.linguaframe.job.domain.vo;

import com.linguaframe.job.domain.enums.PromptTemplatePurpose;

public record PromptTemplateVo(
        String version,
        PromptTemplatePurpose purpose,
        String provider,
        String modelFamily,
        String systemPrompt,
        String outputContract,
        boolean active
) {
}
