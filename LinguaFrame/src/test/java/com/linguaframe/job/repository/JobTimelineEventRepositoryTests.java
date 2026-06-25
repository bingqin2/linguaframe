package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobTimelineEventRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobTimelineEventRepository eventRepository;

    @Test
    void savesAndFindsTimelineEventsByJobIdInOccurrenceOrder() {
        Instant now = Instant.parse("2026-06-26T13:00:00Z");
        createJob("timeline-video-1", "timeline-job-1", now);
        createJob("timeline-video-2", "timeline-job-2", now);
        eventRepository.save(event(
                "timeline-event-2",
                "timeline-job-1",
                LocalizationJobStage.WORKER_SMOKE,
                JobTimelineEventStatus.SUCCEEDED,
                "Smoke stage succeeded.",
                25L,
                null,
                now.plusSeconds(2)
        ));
        eventRepository.save(event(
                "timeline-event-1",
                "timeline-job-1",
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.STARTED,
                "Worker received job.",
                null,
                null,
                now.plusSeconds(1)
        ));
        eventRepository.save(event(
                "timeline-event-other",
                "timeline-job-2",
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.STARTED,
                "Other job event.",
                null,
                null,
                now
        ));

        assertThat(eventRepository.findByJobId("timeline-job-1"))
                .extracting(JobTimelineEventRecord::id)
                .containsExactly("timeline-event-1", "timeline-event-2");
        assertThat(eventRepository.findByJobId("timeline-job-1"))
                .extracting(JobTimelineEventRecord::durationMs)
                .containsExactly(null, 25L);
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

    private JobTimelineEventRecord event(
            String id,
            String jobId,
            LocalizationJobStage stage,
            JobTimelineEventStatus status,
            String message,
            Long durationMs,
            String errorSummary,
            Instant occurredAt
    ) {
        return new JobTimelineEventRecord(
                id,
                jobId,
                stage,
                status,
                message,
                durationMs,
                errorSummary,
                occurredAt
        );
    }
}
