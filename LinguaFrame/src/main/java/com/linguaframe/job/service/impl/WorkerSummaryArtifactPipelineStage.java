package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationPipelineStage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;

@Component
public class WorkerSummaryArtifactPipelineStage implements LocalizationPipelineStage {

    private final JobArtifactService artifactService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public WorkerSummaryArtifactPipelineStage(JobArtifactService artifactService, ObjectMapper objectMapper) {
        this(artifactService, objectMapper, Clock.systemUTC());
    }

    public WorkerSummaryArtifactPipelineStage(JobArtifactService artifactService, Clock clock) {
        this(artifactService, new ObjectMapper(), clock);
    }

    public WorkerSummaryArtifactPipelineStage(
            JobArtifactService artifactService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.artifactService = artifactService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public LocalizationJobStage stage() {
        return LocalizationJobStage.ARTIFACT_SUMMARY;
    }

    @Override
    public void execute(LocalizationJobExecutionContextBo context) {
        artifactService.createArtifact(new CreateJobArtifactCommand(
                context.job().id(),
                JobArtifactType.WORKER_SUMMARY,
                "worker-summary.json",
                "application/json",
                summaryJson(context).getBytes(StandardCharsets.UTF_8)
        ));
    }

    private String summaryJson(LocalizationJobExecutionContextBo context) {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("jobId", context.job().id());
        json.put("videoId", context.job().videoId());
        json.put("targetLanguage", context.job().targetLanguage());
        json.put("demoProfileId", context.job().demoProfileId());
        json.put("subtitleStylePreset", context.job().subtitleStylePreset());
        json.put("translationGlossaryEntryCount", context.job().translationGlossaryEntryCount());
        json.put("translationGlossaryHash", context.job().translationGlossaryHash());
        json.put("subtitlePolishingMode", context.job().subtitlePolishingMode());
        json.put("sourceObjectKey", context.message().sourceObjectKey());
        json.put("stage", stage().name());
        json.put("generatedAt", Instant.now(clock).toString());
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to create worker summary artifact.", ex);
        }
    }
}
