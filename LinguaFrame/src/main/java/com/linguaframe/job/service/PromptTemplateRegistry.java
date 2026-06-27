package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.PromptTemplateVo;

import java.util.List;

public interface PromptTemplateRegistry {

    PromptTemplateVo activeTemplate(PromptTemplatePurpose purpose);

    List<PromptTemplateVo> listActiveTemplates();
}
