package com.linguaframe.job.worker;

import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.service.LocalizationJobExecutionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LocalizationJobWorker {

    private final LocalizationJobExecutionService executionService;

    public LocalizationJobWorker(LocalizationJobExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(
            queues = "${linguaframe.rabbitmq.job-queue}",
            autoStartup = "${linguaframe.worker.execution-enabled:false}"
    )
    public void handle(QueuedLocalizationJobMessage message) {
        executionService.execute(message);
    }
}
