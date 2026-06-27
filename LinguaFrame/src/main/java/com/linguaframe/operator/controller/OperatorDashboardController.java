package com.linguaframe.operator.controller;

import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.service.OperatorDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
public class OperatorDashboardController {

    private final OperatorDashboardService dashboardService;

    public OperatorDashboardController(OperatorDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public OperatorDashboardVo dashboard() {
        return dashboardService.dashboard();
    }
}
