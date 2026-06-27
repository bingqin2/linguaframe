package com.linguaframe.job.scheduling;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.service.RetentionCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RetentionCleanupScheduler {

    private final RetentionCleanupService retentionCleanupService;
    private final LinguaFrameProperties properties;

    public RetentionCleanupScheduler(
            RetentionCleanupService retentionCleanupService,
            LinguaFrameProperties properties
    ) {
        this.retentionCleanupService = retentionCleanupService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${linguaframe.retention.scheduler-interval-ms}")
    public void runScheduledCleanup() {
        LinguaFrameProperties.Retention retention = properties.getRetention();
        if (!retention.isEnabled() || !retention.isSchedulerEnabled()) {
            return;
        }
        retentionCleanupService.runCleanup();
    }
}
