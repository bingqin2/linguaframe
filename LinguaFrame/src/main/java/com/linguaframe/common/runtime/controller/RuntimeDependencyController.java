package com.linguaframe.common.runtime.controller;

import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
@Tag(name = "Runtime Dependencies", description = "Expose non-secret runtime readiness metadata for local and Docker demos.")
public class RuntimeDependencyController {

    private final RuntimeDependencySummaryService summaryService;

    public RuntimeDependencyController(RuntimeDependencySummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/dependencies")
    @Operation(summary = "Get non-secret runtime dependency summary")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Runtime dependency metadata was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public RuntimeDependencySummaryVo getDependencies() {
        return summaryService.getSummary();
    }
}
