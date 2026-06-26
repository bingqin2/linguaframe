package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.WorkerSummaryArtifactPipelineStage;
import com.linguaframe.job.service.impl.LocalizationJobExecutionServiceImpl;
import com.linguaframe.job.service.impl.WorkerSmokePipelineStage;
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
class LocalizationJobExecutionServiceTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private com.linguaframe.common.config.LinguaFrameProperties properties;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void executesQueuedJobAndMarksCompletedWithTimeline() {
        Instant now = Instant.parse("2026-06-26T14:00:00Z");
        createJob("execution-video-1", "execution-job-1", LocalizationJobStatus.QUEUED, now);
        RecordingStage stage = new RecordingStage(false);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-1", "execution-video-1", now));

        assertThat(result.jobId()).isEqualTo("execution-job-1");
        assertThat(result.executed()).isTrue();
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(stage.context.job().id()).isEqualTo("execution-job-1");
        assertThat(jobRepository.findById("execution-job-1"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
                    assertThat(job.startedAt()).isEqualTo(now.plusSeconds(10));
                    assertThat(job.completedAt()).isEqualTo(now.plusSeconds(10));
                });
        assertThat(timelineEventRepository.findByJobId("execution-job-1"))
                .extracting(event -> event.stage() + ":" + event.status())
                .containsExactly(
                        LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                        LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                        LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                        LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                );
    }

    @Test
    void skipsStaleDuplicateMessageForTerminalJob() {
        Instant now = Instant.parse("2026-06-26T15:00:00Z");
        createJob("execution-video-2", "execution-job-2", LocalizationJobStatus.COMPLETED, now);
        RecordingStage stage = new RecordingStage(false);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-2", "execution-video-2", now));

        assertThat(result.executed()).isFalse();
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(stage.context).isNull();
        assertThat(timelineEventRepository.findByJobId("execution-job-2"))
                .extracting(event -> event.stage() + ":" + event.status())
                .containsExactly(LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.SKIPPED);
    }

    @Test
    void failsClaimedJobWhenMessageVideoDoesNotMatchStoredJob() {
        Instant now = Instant.parse("2026-06-26T16:00:00Z");
        createJob("execution-video-3", "execution-job-3", LocalizationJobStatus.QUEUED, now);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(new RecordingStage(false)),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-3", "wrong-video", now));

        assertThat(result.executed()).isTrue();
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.FAILED);
        assertThat(jobRepository.findById("execution-job-3"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.FAILED);
                    assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.WORKER_RECEIVED);
                    assertThat(job.failureReason()).contains("does not match");
                });
        assertThat(timelineEventRepository.findByJobId("execution-job-3"))
                .last()
                .satisfies(event -> {
                    assertThat(event.stage()).isEqualTo(LocalizationJobStage.WORKER_RECEIVED);
                    assertThat(event.status()).isEqualTo(JobTimelineEventStatus.FAILED);
                    assertThat(event.errorSummary()).contains("does not match");
                });
    }

    @Test
    void stageExceptionMarksJobFailedAndRecordsFailedEvent() {
        Instant now = Instant.parse("2026-06-26T17:00:00Z");
        createJob("execution-video-4", "execution-job-4", LocalizationJobStatus.QUEUED, now);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(new RecordingStage(true)),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-4", "execution-video-4", now));

        assertThat(result.executed()).isTrue();
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.FAILED);
        assertThat(jobRepository.findById("execution-job-4"))
                .get()
                .satisfies(job -> {
                    assertThat(job.status()).isEqualTo(LocalizationJobStatus.FAILED);
                    assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.WORKER_SMOKE);
                    assertThat(job.failureReason()).contains("stage exploded");
                });
        assertThat(timelineEventRepository.findByJobId("execution-job-4"))
                .last()
                .satisfies(event -> {
                    assertThat(event.stage()).isEqualTo(LocalizationJobStage.WORKER_SMOKE);
                    assertThat(event.status()).isEqualTo(JobTimelineEventStatus.FAILED);
                    assertThat(event.errorSummary()).contains("stage exploded");
                });
    }

    @Test
    void smokeStageFailureToggleMarksJobFailed() {
        Instant now = Instant.parse("2026-06-26T18:00:00Z");
        createJob("execution-video-5", "execution-job-5", LocalizationJobStatus.QUEUED, now);
        properties.getWorker().setSmokeStageFailureEnabled(true);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(new WorkerSmokePipelineStage(properties)),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-5", "execution-video-5", now));

            assertThat(result.executed()).isTrue();
            assertThat(result.status()).isEqualTo(LocalizationJobStatus.FAILED);
            assertThat(jobRepository.findById("execution-job-5"))
                    .get()
                    .satisfies(job -> {
                        assertThat(job.status()).isEqualTo(LocalizationJobStatus.FAILED);
                        assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.WORKER_SMOKE);
                        assertThat(job.failureReason()).contains("Demo smoke stage failure");
                    });
        } finally {
            properties.getWorker().setSmokeStageFailureEnabled(false);
        }
    }

    @Test
    void workerSummaryStageCreatesArtifactAfterSmokeStageSucceeds() {
        Instant now = Instant.parse("2026-06-26T19:00:00Z");
        createJob("execution-video-6", "execution-job-6", LocalizationJobStatus.QUEUED, now);
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(
                        new WorkerSmokePipelineStage(properties),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-6", "execution-video-6", now));

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(timelineEventRepository.findByJobId("execution-job-6"))
                .extracting(event -> event.stage() + ":" + event.status())
                .containsExactly(
                        LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                        LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                        LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                        LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.STARTED,
                        LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.SUCCEEDED,
                        LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                );
        assertThat(artifactService.commands).hasSize(1);
        CreateJobArtifactCommand command = artifactService.commands.getFirst();
        assertThat(command.jobId()).isEqualTo("execution-job-6");
        assertThat(command.type()).isEqualTo(JobArtifactType.WORKER_SUMMARY);
        assertThat(command.filename()).isEqualTo("worker-summary.json");
        assertThat(command.contentType()).isEqualTo("application/json");
        String json = new String(command.content(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(json)
                .contains("\"jobId\":\"execution-job-6\"")
                .contains("\"videoId\":\"execution-video-6\"")
                .contains("\"targetLanguage\":\"zh-CN\"")
                .contains("\"sourceObjectKey\":\"source-videos/execution-video-6/sample.mp4\"")
                .contains("\"stage\":\"ARTIFACT_SUMMARY\"")
                .contains("\"generatedAt\":\"2026-06-26T19:00:10Z\"");
    }

    private void createJob(String videoId, String jobId, LocalizationJobStatus status, Instant createdAt) {
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
                status,
                createdAt
        ));
    }

    private QueuedLocalizationJobMessage message(String jobId, String videoId, Instant createdAt) {
        return new QueuedLocalizationJobMessage(
                jobId,
                videoId,
                "source-videos/" + videoId + "/sample.mp4",
                "zh-CN",
                createdAt
        );
    }

    private static class RecordingStage implements LocalizationPipelineStage {

        private final boolean fail;
        private LocalizationJobExecutionContextBo context;

        private RecordingStage(boolean fail) {
            this.fail = fail;
        }

        @Override
        public LocalizationJobStage stage() {
            return LocalizationJobStage.WORKER_SMOKE;
        }

        @Override
        public void execute(LocalizationJobExecutionContextBo context) {
            this.context = context;
            if (fail) {
                throw new IllegalStateException("stage exploded");
            }
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            return new JobArtifactVo(
                    "recording-artifact-" + commands.size(),
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    Instant.parse("2026-06-26T19:00:10Z")
            );
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}
