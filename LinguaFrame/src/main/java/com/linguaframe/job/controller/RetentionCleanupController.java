package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.service.RetentionCleanupService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retention/cleanup")
public class RetentionCleanupController {

    private final RetentionCleanupService retentionCleanupService;

    public RetentionCleanupController(RetentionCleanupService retentionCleanupService) {
        this.retentionCleanupService = retentionCleanupService;
    }

    @GetMapping("/preview")
    public RetentionCleanupResultVo previewCleanup() {
        return retentionCleanupService.previewCleanup();
    }

    @PostMapping("/run")
    public RetentionCleanupResultVo runCleanup() {
        return retentionCleanupService.runCleanup();
    }
}
