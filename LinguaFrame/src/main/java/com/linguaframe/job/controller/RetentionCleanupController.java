package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.service.RetentionCleanupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retention/cleanup")
@Tag(name = "Retention Cleanup", description = "Preview and run retention cleanup for demo media, artifacts, and job records.")
public class RetentionCleanupController {

    private final RetentionCleanupService retentionCleanupService;

    public RetentionCleanupController(RetentionCleanupService retentionCleanupService) {
        this.retentionCleanupService = retentionCleanupService;
    }

    @GetMapping("/preview")
    @Operation(summary = "Preview retention cleanup impact")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cleanup candidates and counts were returned without deleting data."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled.")
    })
    public RetentionCleanupResultVo previewCleanup() {
        return retentionCleanupService.previewCleanup();
    }

    @PostMapping("/run")
    @Operation(summary = "Run retention cleanup")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cleanup finished and returned deletion counts."),
            @ApiResponse(responseCode = "401", description = "The private demo token is missing or invalid when demo access is enabled."),
            @ApiResponse(responseCode = "409", description = "Cleanup is disabled or running in dry-run mode.")
    })
    public RetentionCleanupResultVo runCleanup() {
        return retentionCleanupService.runCleanup();
    }
}
