package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.service.impl.JobDispatchOutboxServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JobDispatchOutboxServiceTests {

    private final JobDispatchEventRepository eventRepository = mock(JobDispatchEventRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final JobDispatchOutboxService outboxService = new JobDispatchOutboxServiceImpl(eventRepository, objectMapper);

    @Test
    void enqueuesQueuedJobPayloadWithWorkerSmokeStartStage() throws Exception {
        Instant now = Instant.parse("2026-06-26T10:00:00Z");
        VideoRecord video = new VideoRecord(
                "outbox-video-1",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/outbox-video-1/sample.mp4",
                MediaUploadStatus.UPLOADED,
                now
        );
        LocalizationJobRecord job = new LocalizationJobRecord(
                "outbox-job-1",
                "outbox-video-1",
                "zh-CN",
                "verse",
                LocalizationJobStatus.QUEUED,
                now
        );

        outboxService.enqueueLocalizationJobQueued(video, job);

        var eventCaptor = forClass(JobDispatchEventRecord.class);
        verify(eventRepository).save(eventCaptor.capture());
        JobDispatchEventRecord event = eventCaptor.getValue();
        assertThat(event.jobId()).isEqualTo("outbox-job-1");
        assertThat(event.eventType()).isEqualTo(JobDispatchEventType.LOCALIZATION_JOB_QUEUED);
        assertThat(event.status()).isEqualTo(JobDispatchEventStatus.PENDING);
        assertThat(event.payloadJson()).contains("\"startStage\":\"WORKER_SMOKE\"");
        QueuedLocalizationJobMessage payload = objectMapper.readValue(
                event.payloadJson(),
                QueuedLocalizationJobMessage.class
        );
        assertThat(payload.startStage()).isEqualTo(LocalizationJobStage.WORKER_SMOKE);
        assertThat(payload.ttsVoice()).isEqualTo("verse");
    }
}
