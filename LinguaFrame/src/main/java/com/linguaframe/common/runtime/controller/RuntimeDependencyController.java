package com.linguaframe.common.runtime.controller;

import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeDependencyController {

    private final RuntimeDependencySummaryService summaryService;

    public RuntimeDependencyController(RuntimeDependencySummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/dependencies")
    public RuntimeDependencySummaryVo getDependencies() {
        return summaryService.getSummary();
    }
}
