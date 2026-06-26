package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.service.LocalizationPipelineStage;
import org.springframework.stereotype.Component;

@Component
public class WorkerSmokePipelineStage implements LocalizationPipelineStage {

    private final LinguaFrameProperties properties;

    public WorkerSmokePipelineStage(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.WORKER_SMOKE;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        if (properties.getWorker().isSmokeStageFailureEnabled()) {
            throw new IllegalStateException("Demo smoke stage failure is enabled.");
        }

        long durationMs = properties.getWorker().getSmokeStageDurationMs();
        if (durationMs <= 0) {
            return;
        }
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Worker smoke stage was interrupted.", ex);
        }
    }
}
