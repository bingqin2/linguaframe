package com.linguaframe.job.scheduling;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.service.RetentionCleanupService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionCleanupSchedulerTests {

    @Test
    void runsCleanupWhenRetentionAndSchedulerAreEnabled() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        when(service.runCleanup()).thenReturn(new RetentionCleanupResultVo(false, 1, 1, 1, 2, 0, 0));
        RetentionCleanupScheduler scheduler = new RetentionCleanupScheduler(service, properties(true, true));

        scheduler.runScheduledCleanup();

        verify(service).runCleanup();
    }

    @Test
    void skipsCleanupWhenRetentionIsDisabled() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        RetentionCleanupScheduler scheduler = new RetentionCleanupScheduler(service, properties(false, true));

        scheduler.runScheduledCleanup();

        verify(service, never()).runCleanup();
    }

    @Test
    void skipsCleanupWhenSchedulerIsDisabled() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        RetentionCleanupScheduler scheduler = new RetentionCleanupScheduler(service, properties(true, false));

        scheduler.runScheduledCleanup();

        verify(service, never()).runCleanup();
    }

    private LinguaFrameProperties properties(boolean retentionEnabled, boolean schedulerEnabled) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getRetention().setEnabled(retentionEnabled);
        properties.getRetention().setSchedulerEnabled(schedulerEnabled);
        return properties;
    }
}
