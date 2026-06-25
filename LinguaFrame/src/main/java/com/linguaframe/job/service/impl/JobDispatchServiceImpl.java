package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobDispatchResultVo;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.service.JobDispatchService;
import com.linguaframe.job.service.JobQueuePublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class JobDispatchServiceImpl implements JobDispatchService {

    private static final int RETRY_DELAY_SECONDS = 30;

    private final JobDispatchEventRepository eventRepository;
    private final JobQueuePublisher publisher;
    private final ObjectMapper objectMapper;
    private final LinguaFrameProperties properties;
    private final Clock clock;

    @Autowired
    public JobDispatchServiceImpl(
            JobDispatchEventRepository eventRepository,
            JobQueuePublisher publisher,
            ObjectMapper objectMapper,
            LinguaFrameProperties properties
    ) {
        this(eventRepository, publisher, objectMapper, properties, Clock.systemUTC());
    }

    public JobDispatchServiceImpl(
            JobDispatchEventRepository eventRepository,
            JobQueuePublisher publisher,
            ObjectMapper objectMapper,
            LinguaFrameProperties properties,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public JobDispatchResultVo dispatchReadyEvents(int limit) {
        int safeLimit = limit <= 0 ? properties.getWorker().getDispatchBatchSize() : limit;
        Instant now = Instant.now(clock);
        List<JobDispatchEventRecord> readyEvents = eventRepository.findReadyToDispatch(now, safeLimit);

        int dispatched = 0;
        int failed = 0;
        for (JobDispatchEventRecord event : readyEvents) {
            try {
                QueuedLocalizationJobMessage message = readPayload(event);
                publisher.publish(message);
                eventRepository.markDispatched(event.id(), Instant.now(clock));
                dispatched++;
            } catch (RuntimeException ex) {
                markFailed(event, ex);
                failed++;
            }
        }

        return new JobDispatchResultVo(readyEvents.size(), dispatched, failed);
    }

    private QueuedLocalizationJobMessage readPayload(JobDispatchEventRecord event) {
        try {
            return objectMapper.readValue(event.payloadJson(), QueuedLocalizationJobMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to read dispatch payload.", ex);
        }
    }

    private void markFailed(JobDispatchEventRecord event, RuntimeException ex) {
        Instant updatedAt = Instant.now(clock);
        eventRepository.markFailed(
                event.id(),
                event.attempts() + 1,
                updatedAt.plusSeconds(RETRY_DELAY_SECONDS),
                safeError(ex),
                updatedAt
        );
    }

    private String safeError(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message;
    }
}
