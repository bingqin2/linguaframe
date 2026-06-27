package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.service.PromptTemplateRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/prompt-templates")
@Tag(name = "Prompt Templates", description = "Inspect active prompt templates used by the AI pipeline.")
public class PromptTemplateController {

    private final PromptTemplateRegistry promptTemplateRegistry;

    public PromptTemplateController(PromptTemplateRegistry promptTemplateRegistry) {
        this.promptTemplateRegistry = promptTemplateRegistry;
    }

    @GetMapping
    @Operation(summary = "List active prompt templates")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active prompt templates were listed."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public List<PromptTemplateVo> listActiveTemplates() {
        return promptTemplateRegistry.listActiveTemplates();
    }
}
