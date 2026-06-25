package com.linguaframe.job.scheduling;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.service.JobDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "linguaframe.worker", name = "dispatch-enabled", havingValue = "true")
public class JobDispatchScheduler {

    private final JobDispatchService dispatchService;
    private final LinguaFrameProperties properties;

    public JobDispatchScheduler(JobDispatchService dispatchService, LinguaFrameProperties properties) {
        this.dispatchService = dispatchService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${linguaframe.worker.dispatch-interval-ms}")
    public void dispatchReadyEvents() {
        dispatchService.dispatchReadyEvents(properties.getWorker().getDispatchBatchSize());
    }
}
