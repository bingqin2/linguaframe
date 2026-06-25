package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.LocalizationJobRetryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class LocalizationJobController {

    private final LocalizationJobQueryService queryService;
    private final LocalizationJobRetryService retryService;

    public LocalizationJobController(LocalizationJobQueryService queryService, LocalizationJobRetryService retryService) {
        this.queryService = queryService;
        this.retryService = retryService;
    }

    @GetMapping("/{jobId}")
    public LocalizationJobVo getJob(@PathVariable String jobId) {
        return queryService.getJob(jobId);
    }

    @PostMapping("/{jobId}/retry")
    public LocalizationJobVo retryJob(@PathVariable String jobId) {
        return retryService.retryFailedJob(jobId);
    }
}
