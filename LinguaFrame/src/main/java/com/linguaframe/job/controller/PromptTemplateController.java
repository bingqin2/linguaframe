package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.service.PromptTemplateRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prompt-templates")
public class PromptTemplateController {

    private final PromptTemplateRegistry promptTemplateRegistry;

    public PromptTemplateController(PromptTemplateRegistry promptTemplateRegistry) {
        this.promptTemplateRegistry = promptTemplateRegistry;
    }

    @GetMapping
    public List<PromptTemplateVo> listActiveTemplates() {
        return promptTemplateRegistry.listActiveTemplates();
    }
}
