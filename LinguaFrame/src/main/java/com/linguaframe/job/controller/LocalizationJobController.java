package com.linguaframe.job.controller;

import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class LocalizationJobController {

    private final LocalizationJobQueryService queryService;

    public LocalizationJobController(LocalizationJobQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{jobId}")
    public LocalizationJobVo getJob(@PathVariable String jobId) {
        return queryService.getJob(jobId);
    }
}
