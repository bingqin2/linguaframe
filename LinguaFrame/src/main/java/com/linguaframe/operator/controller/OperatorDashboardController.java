package com.linguaframe.operator.controller;

import com.linguaframe.operator.domain.bo.DemoSessionEvidencePackageBo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessEvidenceVo;
import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionBoardVo;
import com.linguaframe.operator.service.DemoPresentationCockpitService;
import com.linguaframe.operator.service.DemoRunLauncherService;
import com.linguaframe.operator.service.DemoSampleMediaCatalogService;
import com.linguaframe.operator.service.DemoSessionCommandCenterService;
import com.linguaframe.operator.service.DemoSessionEvidencePackageService;
import com.linguaframe.operator.service.DemoSessionRecoveryBoardService;
import com.linguaframe.operator.service.ModelUsageLedgerService;
import com.linguaframe.operator.service.OpenAiReadinessEvidenceService;
import com.linguaframe.operator.service.OperatorDashboardService;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import com.linguaframe.operator.service.PrivateDemoRunArchiveService;
import com.linguaframe.operator.service.PrivateDemoDeliveryReceiptService;
import com.linguaframe.operator.service.SessionNarrationProductionBoardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final PrivateDemoDeliveryReceiptService deliveryReceiptService;
    private final DemoSampleMediaCatalogService sampleMediaCatalogService;
    private final DemoRunLauncherService demoRunLauncherService;
    private final DemoPresentationCockpitService demoPresentationCockpitService;
    private final ModelUsageLedgerService modelUsageLedgerService;
    private final DemoSessionCommandCenterService demoSessionCommandCenterService;
    private final DemoSessionEvidencePackageService demoSessionEvidencePackageService;
    private final DemoSessionRecoveryBoardService demoSessionRecoveryBoardService;
    private final OpenAiReadinessEvidenceService openAiReadinessEvidenceService;
    private final SessionNarrationProductionBoardService sessionNarrationProductionBoardService;

    public OperatorDashboardController(
            OperatorDashboardService dashboardService,
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService,
            PrivateDemoRunArchiveService runArchiveService,
            PrivateDemoDeliveryReceiptService deliveryReceiptService,
            DemoSampleMediaCatalogService sampleMediaCatalogService,
            DemoRunLauncherService demoRunLauncherService,
            DemoPresentationCockpitService demoPresentationCockpitService,
            ModelUsageLedgerService modelUsageLedgerService,
            DemoSessionCommandCenterService demoSessionCommandCenterService,
            DemoSessionEvidencePackageService demoSessionEvidencePackageService,
            DemoSessionRecoveryBoardService demoSessionRecoveryBoardService,
            OpenAiReadinessEvidenceService openAiReadinessEvidenceService,
            SessionNarrationProductionBoardService sessionNarrationProductionBoardService
    ) {
        this.dashboardService = dashboardService;
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.evidenceGalleryService = evidenceGalleryService;
        this.runArchiveService = runArchiveService;
        this.deliveryReceiptService = deliveryReceiptService;
        this.sampleMediaCatalogService = sampleMediaCatalogService;
        this.demoRunLauncherService = demoRunLauncherService;
        this.demoPresentationCockpitService = demoPresentationCockpitService;
        this.modelUsageLedgerService = modelUsageLedgerService;
        this.demoSessionCommandCenterService = demoSessionCommandCenterService;
        this.demoSessionEvidencePackageService = demoSessionEvidencePackageService;
        this.demoSessionRecoveryBoardService = demoSessionRecoveryBoardService;
        this.openAiReadinessEvidenceService = openAiReadinessEvidenceService;
        this.sessionNarrationProductionBoardService = sessionNarrationProductionBoardService;
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

    @GetMapping("/private-demo/delivery-receipt")
    @Operation(summary = "Get private demo delivery receipt")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo delivery receipt was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public PrivateDemoDeliveryReceiptVo privateDemoDeliveryReceipt(
            @RequestParam(required = false) String jobId
    ) {
        return deliveryReceiptService.receipt(jobId);
    }

    @GetMapping(value = "/private-demo/delivery-receipt/markdown/download", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @Operation(summary = "Download private demo delivery receipt markdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo delivery receipt markdown was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<String> privateDemoDeliveryReceiptMarkdown(
            @RequestParam(required = false) String jobId
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"private-demo-delivery-receipt.md\"")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(deliveryReceiptService.receiptMarkdown(jobId));
    }

    @GetMapping(value = "/private-demo/delivery-receipt/download", produces = "application/zip")
    @Operation(summary = "Download private demo delivery receipt package")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Private demo delivery receipt ZIP was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<InputStreamResource> privateDemoDeliveryReceiptPackage(
            @RequestParam(required = false) String jobId
    ) {
        DemoSessionEvidencePackageBo receiptPackage = deliveryReceiptService.openPackage(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + receiptPackage.filename() + "\"")
                .contentLength(receiptPackage.sizeBytes())
                .contentType(MediaType.parseMediaType(receiptPackage.contentType()))
                .body(new InputStreamResource(receiptPackage.inputStream()));
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

    @GetMapping("/demo-run-launcher")
    @Operation(summary = "Get demo run launcher")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo run launcher was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public DemoRunLauncherVo demoRunLauncher() {
        return demoRunLauncherService.launcher();
    }

    @GetMapping("/demo-presentation-cockpit")
    @Operation(summary = "Get demo presentation cockpit")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo presentation cockpit was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public DemoPresentationCockpitVo demoPresentationCockpit(
            @RequestParam(required = false) String jobId
    ) {
        return demoPresentationCockpitService.cockpit(jobId);
    }

    @GetMapping("/model-usage-ledger")
    @Operation(summary = "Get owner-scoped model usage ledger")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model usage ledger was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ModelUsageLedgerVo modelUsageLedger(@RequestParam(required = false) Integer limit) {
        return modelUsageLedgerService.ledger(limit);
    }

    @GetMapping(value = "/model-usage-ledger/markdown/download", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @Operation(summary = "Download model usage ledger markdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Model usage ledger markdown was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<String> modelUsageLedgerMarkdown(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"model-usage-ledger.md\"")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(modelUsageLedgerService.ledgerMarkdown(limit));
    }

    @GetMapping("/demo-session-command-center")
    @Operation(summary = "Get demo session command center")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo session command center was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public DemoSessionCommandCenterVo demoSessionCommandCenter(
            @RequestParam(required = false) String jobId
    ) {
        return demoSessionCommandCenterService.commandCenter(jobId);
    }

    @GetMapping(value = "/demo-session-command-center/markdown/download", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @Operation(summary = "Download demo session command center markdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo session command center markdown was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<String> demoSessionCommandCenterMarkdown(
            @RequestParam(required = false) String jobId
    ) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo-session-command-center.md\"")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(demoSessionCommandCenterService.commandCenterMarkdown(jobId));
    }

    @GetMapping("/demo-session-recovery-board")
    @Operation(summary = "Get demo session recovery board")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo session recovery board was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public DemoSessionRecoveryBoardVo demoSessionRecoveryBoard(@RequestParam(required = false) Integer limit) {
        return demoSessionRecoveryBoardService.board(limit);
    }

    @GetMapping(value = "/demo-session-recovery-board/markdown/download", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @Operation(summary = "Download demo session recovery board markdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo session recovery board markdown was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<String> demoSessionRecoveryBoardMarkdown(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo-session-recovery-board.md\"")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(demoSessionRecoveryBoardService.boardMarkdown(limit));
    }

    @GetMapping("/session-narration-production-board")
    @Operation(summary = "Get session narration production board")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session narration production board was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public SessionNarrationProductionBoardVo sessionNarrationProductionBoard(@RequestParam(required = false) Integer limit) {
        return sessionNarrationProductionBoardService.board(limit);
    }

    @GetMapping(value = "/session-narration-production-board/markdown/download", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @Operation(summary = "Download session narration production board markdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session narration production board markdown was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<String> sessionNarrationProductionBoardMarkdown(@RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"session-narration-production-board.md\"")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(sessionNarrationProductionBoardService.boardMarkdown(limit));
    }

    @GetMapping(value = "/demo-session-evidence-package/download", produces = "application/zip")
    @Operation(summary = "Download demo session evidence package")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Demo session evidence package ZIP was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<InputStreamResource> demoSessionEvidencePackage(
            @RequestParam(required = false) String jobId
    ) {
        DemoSessionEvidencePackageBo evidencePackage = demoSessionEvidencePackageService.openPackage(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + evidencePackage.filename() + "\"")
                .contentLength(evidencePackage.sizeBytes())
                .contentType(MediaType.parseMediaType(evidencePackage.contentType()))
                .body(new InputStreamResource(evidencePackage.inputStream()));
    }

    @GetMapping("/openai-readiness-evidence")
    @Operation(summary = "Get OpenAI readiness evidence")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OpenAI readiness evidence was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public OpenAiReadinessEvidenceVo openAiReadinessEvidence() {
        return openAiReadinessEvidenceService.getEvidence();
    }

    @GetMapping(value = "/openai-readiness-evidence/markdown/download", produces = MediaType.TEXT_MARKDOWN_VALUE)
    @Operation(summary = "Download OpenAI readiness evidence markdown")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OpenAI readiness evidence markdown was returned."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public ResponseEntity<String> openAiReadinessEvidenceMarkdown() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"openai-readiness-evidence.md\"")
                .contentType(MediaType.TEXT_MARKDOWN)
                .body(openAiReadinessEvidenceService.evidenceMarkdown());
    }
}
