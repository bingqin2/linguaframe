package com.linguaframe.operator.controller;

import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.service.OperatorDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
@Tag(name = "Operator Dashboard", description = "Expose safe operator-facing demo health and recent job summary data.")
public class OperatorDashboardController {

    private final OperatorDashboardService dashboardService;

    public OperatorDashboardController(OperatorDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get operator dashboard summary")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Operator dashboard summary was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public OperatorDashboardVo dashboard() {
        return dashboardService.dashboard();
    }
}
