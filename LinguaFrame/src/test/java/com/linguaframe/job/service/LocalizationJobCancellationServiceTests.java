package com.linguaframe.job.service;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.exception.JobStateConflictException;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.LocalizationJobCancellationServiceImpl;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LocalizationJobCancellationServiceTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

    @Autowired
    private LocalizationJobQueryService queryService;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM quality_evaluations").update();
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void cancelsQueuedJobAndRecordsTimeline() {
        Instant now = Instant.parse("2026-06-27T03:20:00Z");
        createJob("cancel-service-video", "cancel-service-job", LocalizationJobStatus.QUEUED, now);
        LocalizationJobCancellationService service = service(Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

        LocalizationJobVo result = service.cancelJob("cancel-service-job");

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
        assertThat(result.completedAt()).isEqualTo(now.plusSeconds(1));
        assertThat(timelineEventRepository.findByJobId("cancel-service-job"))
                .last()
                .satisfies(event -> {
                    assertThat(event.stage()).isEqualTo(LocalizationJobStage.WORKER_RECEIVED);
                    assertThat(event.status()).isEqualTo(JobTimelineEventStatus.SKIPPED);
                    assertThat(event.message()).isEqualTo("Cancellation requested.");
                });
    }

    @Test
    void rejectsTerminalJobCancellation() {
        Instant now = Instant.parse("2026-06-27T03:30:00Z");
        createJob("cancel-service-video-terminal", "cancel-service-job-terminal", LocalizationJobStatus.COMPLETED, now);
        LocalizationJobCancellationService service = service(Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.cancelJob("cancel-service-job-terminal"))
                .isInstanceOf(JobStateConflictException.class)
                .hasMessage("Only queued, retrying, or processing localization jobs can be cancelled.");
    }

    private LocalizationJobCancellationService service(Clock clock) {
        return new LocalizationJobCancellationServiceImpl(
                jobRepository,
                timelineEventRepository,
                queryService,
                clock
        );
    }

    private void createJob(String videoId, String jobId, LocalizationJobStatus status, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample-" + videoId + ".mp4",
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
                status,
                createdAt
        ));
    }
}
