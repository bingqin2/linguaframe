package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.service.JobDispatchOutboxService;
import com.linguaframe.media.domain.entity.VideoRecord;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class JobDispatchOutboxServiceImpl implements JobDispatchOutboxService {

    private final JobDispatchEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    public JobDispatchOutboxServiceImpl(JobDispatchEventRepository eventRepository, ObjectMapper objectMapper) {
        this.eventRepository = eventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void enqueueLocalizationJobQueued(VideoRecord video, LocalizationJobRecord job) {
        Instant now = Instant.now();
        QueuedLocalizationJobMessage message = new QueuedLocalizationJobMessage(
                job.id(),
                video.id(),
                video.sourceObjectKey(),
                job.targetLanguage(),
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryJson(),
                job.translationGlossaryHash(),
                job.translationGlossaryEntryCount(),
                job.subtitlePolishingMode(),
                job.demoProfileId(),
                job.createdAt(),
                LocalizationJobStage.WORKER_SMOKE
        );

        try {
            eventRepository.save(new JobDispatchEventRecord(
                    UUID.randomUUID().toString(),
                    job.id(),
                    JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                    objectMapper.writeValueAsString(message),
                    JobDispatchEventStatus.PENDING,
                    0,
                    now,
                    null,
                    null,
                    now,
                    now
            ));
        } catch (JsonProcessingException | RuntimeException ex) {
            throw new IllegalStateException("Failed to enqueue localization job.", ex);
        }
    }
}
