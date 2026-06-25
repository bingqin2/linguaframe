package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.JobDispatchServiceImpl;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobDispatchServiceTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository eventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LinguaFrameProperties properties;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void dispatchesReadyPendingEventAndMarksDispatched() throws Exception {
        Instant now = Instant.parse("2026-06-26T05:00:00Z");
        createJob("dispatch-service-video-1", "dispatch-service-job-1", now);
        eventRepository.save(event(
                "dispatch-service-event-1",
                "dispatch-service-job-1",
                new QueuedLocalizationJobMessage(
                        "dispatch-service-job-1",
                        "dispatch-service-video-1",
                        "source-videos/dispatch-service-video-1/sample.mp4",
                        "zh-CN",
                        now
                ),
                JobDispatchEventStatus.PENDING,
                0,
                now.minusSeconds(1),
                now
        ));
        RecordingPublisher publisher = new RecordingPublisher(false);
        JobDispatchService service = new JobDispatchServiceImpl(
                eventRepository,
                publisher,
                objectMapper,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.dispatchReadyEvents(10);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(result.failed()).isZero();
        assertThat(publisher.messages)
                .extracting(QueuedLocalizationJobMessage::jobId)
                .containsExactly("dispatch-service-job-1");
        assertThat(eventRepository.findLatestByJobId("dispatch-service-job-1"))
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.DISPATCHED);
                    assertThat(event.dispatchedAt()).isNotNull();
                    assertThat(event.lastError()).isNull();
                });
    }

    @Test
    void marksPublisherFailureAsFailed() throws Exception {
        Instant now = Instant.parse("2026-06-26T06:00:00Z");
        createJob("dispatch-service-video-2", "dispatch-service-job-2", now);
        eventRepository.save(event(
                "dispatch-service-event-2",
                "dispatch-service-job-2",
                new QueuedLocalizationJobMessage(
                        "dispatch-service-job-2",
                        "dispatch-service-video-2",
                        "source-videos/dispatch-service-video-2/sample.mp4",
                        "zh-CN",
                        now
                ),
                JobDispatchEventStatus.PENDING,
                0,
                now.minusSeconds(1),
                now
        ));
        RecordingPublisher publisher = new RecordingPublisher(true);
        JobDispatchService service = new JobDispatchServiceImpl(
                eventRepository,
                publisher,
                objectMapper,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.dispatchReadyEvents(10);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.dispatched()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(eventRepository.findLatestByJobId("dispatch-service-job-2"))
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.FAILED);
                    assertThat(event.attempts()).isEqualTo(1);
                    assertThat(event.lastError()).contains("publisher unavailable");
                    assertThat(event.nextAttemptAt()).isAfter(now);
                });
    }

    @Test
    void marksMalformedPayloadAsFailedWithoutPublishing() {
        Instant now = Instant.parse("2026-06-26T07:00:00Z");
        createJob("dispatch-service-video-3", "dispatch-service-job-3", now);
        eventRepository.save(new JobDispatchEventRecord(
                "dispatch-service-event-3",
                "dispatch-service-job-3",
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{not-json",
                JobDispatchEventStatus.PENDING,
                0,
                now.minusSeconds(1),
                null,
                null,
                now,
                now
        ));
        RecordingPublisher publisher = new RecordingPublisher(false);
        JobDispatchService service = new JobDispatchServiceImpl(
                eventRepository,
                publisher,
                objectMapper,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.dispatchReadyEvents(10);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.dispatched()).isZero();
        assertThat(result.failed()).isEqualTo(1);
        assertThat(publisher.messages).isEmpty();
        assertThat(eventRepository.findLatestByJobId("dispatch-service-job-3"))
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.FAILED);
                    assertThat(event.attempts()).isEqualTo(1);
                    assertThat(event.lastError()).contains("Failed to read dispatch payload");
                });
    }

    @Test
    void respectsDispatchLimit() throws Exception {
        Instant now = Instant.parse("2026-06-26T08:00:00Z");
        createJob("dispatch-service-video-4", "dispatch-service-job-4", now);
        createJob("dispatch-service-video-5", "dispatch-service-job-5", now);
        eventRepository.save(event(
                "dispatch-service-event-4",
                "dispatch-service-job-4",
                new QueuedLocalizationJobMessage("dispatch-service-job-4", "dispatch-service-video-4", "source-4", "zh-CN", now),
                JobDispatchEventStatus.PENDING,
                0,
                now.minusSeconds(1),
                now
        ));
        eventRepository.save(event(
                "dispatch-service-event-5",
                "dispatch-service-job-5",
                new QueuedLocalizationJobMessage("dispatch-service-job-5", "dispatch-service-video-5", "source-5", "zh-CN", now),
                JobDispatchEventStatus.PENDING,
                0,
                now.minusSeconds(1),
                now.plusSeconds(1)
        ));
        RecordingPublisher publisher = new RecordingPublisher(false);
        JobDispatchService service = new JobDispatchServiceImpl(
                eventRepository,
                publisher,
                objectMapper,
                properties,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        var result = service.dispatchReadyEvents(1);

        assertThat(result.scanned()).isEqualTo(1);
        assertThat(result.dispatched()).isEqualTo(1);
        assertThat(publisher.messages).hasSize(1);
    }

    private void createJob(String videoId, String jobId, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/" + videoId + "/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
    }

    private JobDispatchEventRecord event(
            String eventId,
            String jobId,
            QueuedLocalizationJobMessage message,
            JobDispatchEventStatus status,
            int attempts,
            Instant nextAttemptAt,
            Instant createdAt
    ) throws Exception {
        return new JobDispatchEventRecord(
                eventId,
                jobId,
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                objectMapper.writeValueAsString(message),
                status,
                attempts,
                nextAttemptAt,
                null,
                null,
                createdAt,
                createdAt
        );
    }

    private static class RecordingPublisher implements JobQueuePublisher {

        private final boolean fail;
        private final List<QueuedLocalizationJobMessage> messages = new ArrayList<>();

        private RecordingPublisher(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void publish(QueuedLocalizationJobMessage message) {
            if (fail) {
                throw new IllegalStateException("publisher unavailable");
            }
            messages.add(message);
        }
    }
}
