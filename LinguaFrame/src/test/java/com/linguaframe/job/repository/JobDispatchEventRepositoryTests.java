package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobDispatchEventRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository eventRepository;

    @Test
    void savesAndFindsLatestEventByJobId() {
        Instant now = Instant.parse("2026-06-26T01:00:00Z");
        createJob("dispatch-video-1", "dispatch-job-1", now);
        JobDispatchEventRecord event = new JobDispatchEventRecord(
                "dispatch-event-1",
                "dispatch-job-1",
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{\"jobId\":\"dispatch-job-1\"}",
                JobDispatchEventStatus.PENDING,
                0,
                now,
                null,
                null,
                now,
                now
        );

        eventRepository.save(event);

        assertThat(eventRepository.findLatestByJobId("dispatch-job-1")).contains(event);
    }

    @Test
    void findsReadyEventsByStatusTimeOrderAndLimit() {
        Instant now = Instant.parse("2026-06-26T02:00:00Z");
        createJob("dispatch-video-2", "dispatch-job-2", now);
        createJob("dispatch-video-3", "dispatch-job-3", now);
        createJob("dispatch-video-4", "dispatch-job-4", now);
        eventRepository.save(event(
                "dispatch-event-ready-2",
                "dispatch-job-2",
                JobDispatchEventStatus.PENDING,
                0,
                now.minusSeconds(5),
                now.minusSeconds(5)
        ));
        eventRepository.save(event(
                "dispatch-event-ready-1",
                "dispatch-job-3",
                JobDispatchEventStatus.FAILED,
                1,
                now.minusSeconds(10),
                now.minusSeconds(10)
        ));
        eventRepository.save(event(
                "dispatch-event-later",
                "dispatch-job-4",
                JobDispatchEventStatus.PENDING,
                0,
                now.plusSeconds(60),
                now.minusSeconds(20)
        ));

        List<JobDispatchEventRecord> ready = eventRepository.findReadyToDispatch(now, 1);

        assertThat(ready)
                .extracting(JobDispatchEventRecord::id)
                .containsExactly("dispatch-event-ready-1");
    }

    @Test
    void marksEventAsDispatched() {
        Instant now = Instant.parse("2026-06-26T03:00:00Z");
        createJob("dispatch-video-5", "dispatch-job-5", now);
        eventRepository.save(event("dispatch-event-success", "dispatch-job-5", JobDispatchEventStatus.PENDING, 0, now, now));

        Instant dispatchedAt = now.plusSeconds(3);
        eventRepository.markDispatched("dispatch-event-success", dispatchedAt);

        assertThat(eventRepository.findLatestByJobId("dispatch-job-5"))
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.DISPATCHED);
                    assertThat(event.dispatchedAt()).isEqualTo(dispatchedAt);
                    assertThat(event.updatedAt()).isEqualTo(dispatchedAt);
                });
    }

    @Test
    void marksEventAsFailedWithTruncatedSafeError() {
        Instant now = Instant.parse("2026-06-26T04:00:00Z");
        createJob("dispatch-video-6", "dispatch-job-6", now);
        eventRepository.save(event("dispatch-event-failed", "dispatch-job-6", JobDispatchEventStatus.PENDING, 0, now, now));

        String longError = "x".repeat(700);
        Instant nextAttemptAt = now.plusSeconds(30);
        Instant updatedAt = now.plusSeconds(1);
        eventRepository.markFailed("dispatch-event-failed", 1, nextAttemptAt, longError, updatedAt);

        assertThat(eventRepository.findLatestByJobId("dispatch-job-6"))
                .get()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(JobDispatchEventStatus.FAILED);
                    assertThat(event.attempts()).isEqualTo(1);
                    assertThat(event.nextAttemptAt()).isEqualTo(nextAttemptAt);
                    assertThat(event.updatedAt()).isEqualTo(updatedAt);
                    assertThat(event.lastError()).hasSize(512);
                });
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
            JobDispatchEventStatus status,
            int attempts,
            Instant nextAttemptAt,
            Instant createdAt
    ) {
        return new JobDispatchEventRecord(
                eventId,
                jobId,
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{\"jobId\":\"" + jobId + "\"}",
                status,
                attempts,
                nextAttemptAt,
                null,
                null,
                createdAt,
                createdAt
        );
    }
}
