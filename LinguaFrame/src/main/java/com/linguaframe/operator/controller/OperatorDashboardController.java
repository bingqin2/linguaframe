package com.linguaframe.operator.controller;

import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.DemoSampleMediaCatalogService;
import com.linguaframe.operator.service.OperatorDashboardService;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import com.linguaframe.operator.service.PrivateDemoRunArchiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/operator")
@Tag(name = "Operator Dashboard", description = "Expose safe operator-facing demo health and recent job summary data.")
public class OperatorDashboardController {

    private final OperatorDashboardService dashboardService;
    private final PrivateDemoOperationsService operationsService;
    private final PrivateDemoLaunchRehearsalService launchRehearsalService;
    private final PrivateDemoEvidenceGalleryService evidenceGalleryService;
    private final PrivateDemoRunArchiveService runArchiveService;
    private final DemoSampleMediaCatalogService sampleMediaCatalogService;

    public OperatorDashboardController(
            OperatorDashboardService dashboardService,
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService,
            PrivateDemoRunArchiveService runArchiveService,
            DemoSampleMediaCatalogService sampleMediaCatalogService
    ) {
        this.dashboardService = dashboardService;
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.evidenceGalleryService = evidenceGalleryService;
        this.runArchiveService = runArchiveService;
        this.sampleMediaCatalogService = sampleMediaCatalogService;
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

    @GetMapping("/private-demo/operations")
    @Operation(summary = "Get private demo operations readiness")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo operations readiness was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public PrivateDemoOperationsVo privateDemoOperations() {
        return operationsService.operations();
    }

    @GetMapping("/private-demo/launch-rehearsal")
    @Operation(summary = "Get private demo launch rehearsal checklist")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo launch rehearsal checklist was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public PrivateDemoLaunchRehearsalVo privateDemoLaunchRehearsal() {
        return launchRehearsalService.launchRehearsal();
    }

    @GetMapping("/private-demo/evidence-gallery")
    @Operation(summary = "Get private demo evidence gallery")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo evidence gallery was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public PrivateDemoEvidenceGalleryVo privateDemoEvidenceGallery(
            @RequestParam(required = false) Integer limit
    ) {
        return evidenceGalleryService.evidenceGallery(limit);
    }

    @GetMapping("/private-demo/run-archive")
    @Operation(summary = "Get private demo run archive")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo run archive was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public PrivateDemoRunArchiveVo privateDemoRunArchive() {
        return runArchiveService.runArchive();
    }

    @GetMapping("/demo-sample-media-catalog")
    @Operation(summary = "Get demo sample media catalog")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo sample media catalog was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public DemoSampleMediaCatalogVo demoSampleMediaCatalog() {
        return sampleMediaCatalogService.catalog();
    }
}
