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
import com.linguaframe.job.service.impl.AudioExtractionPipelineStage;
import com.linguaframe.job.service.impl.LocalizationJobExecutionServiceImpl;
import com.linguaframe.job.service.impl.WorkerSmokePipelineStage;
import com.linguaframe.media.domain.bo.ExtractAudioCommand;
import com.linguaframe.media.domain.bo.ExtractedAudioBo;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.FfmpegAudioExtractionService;
import com.linguaframe.media.service.MediaWorkDirectoryService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test
    void audioExtractionStageCreatesArtifactBetweenSmokeAndSummaryAndCleansWorkDirectory(@TempDir Path tempDir)
            throws IOException {
        Instant now = Instant.parse("2026-06-26T20:00:00Z");
        createJob("execution-video-7", "execution-job-7", LocalizationJobStatus.QUEUED, now);
        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] audioBytes = new byte[] {7, 8, 9};
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", audioBytes)
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        properties.getFfmpeg().setAudioEnabled(true);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(
                        new WorkerSmokePipelineStage(properties),
                        new AudioExtractionPipelineStage(
                                properties,
                                objectStorageService,
                                workDirectoryService,
                                audioExtractionService,
                                artifactService
                        ),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-7", "execution-video-7", now));

            assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
            assertThat(timelineEventRepository.findByJobId("execution-job-7"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(
                            LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                    );
            assertThat(objectStorageService.openedObjectKeys)
                    .containsExactly("source-videos/execution-video-7/sample.mp4");
            assertThat(workDirectoryService.createdJobIds).containsExactly("execution-job-7");
            assertThat(workDirectoryService.cleanedDirectories).containsExactly(workDirectoryService.workDirectory);
            assertThat(audioExtractionService.command.jobId()).isEqualTo("execution-job-7");
            assertThat(audioExtractionService.command.inputVideoPath()).isEqualTo(workDirectoryService.workDirectory.resolve("source-video"));
            assertThat(audioExtractionService.command.outputAudioPath()).isEqualTo(workDirectoryService.workDirectory.resolve("audio.wav"));
            assertThat(Files.readAllBytes(audioExtractionService.command.inputVideoPath())).containsExactly(sourceBytes);
            assertThat(artifactService.commands)
                    .extracting(CreateJobArtifactCommand::type)
                    .containsExactly(JobArtifactType.EXTRACTED_AUDIO, JobArtifactType.WORKER_SUMMARY);
            CreateJobArtifactCommand command = artifactService.commands.getFirst();
            assertThat(command.jobId()).isEqualTo("execution-job-7");
            assertThat(command.type()).isEqualTo(JobArtifactType.EXTRACTED_AUDIO);
            assertThat(command.filename()).isEqualTo("audio.wav");
            assertThat(command.contentType()).isEqualTo("audio/wav");
            assertThat(command.content()).containsExactly(audioBytes);
        } finally {
            properties.getFfmpeg().setAudioEnabled(false);
        }
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

    private static class RecordingObjectStorageService implements ObjectStorageService {

        private final byte[] content;
        private final List<String> openedObjectKeys = new ArrayList<>();

        private RecordingObjectStorageService(byte[] content) {
            this.content = content;
        }

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String objectKey) {
            openedObjectKeys.add(objectKey);
            return new ByteArrayInputStream(content);
        }
    }

    private static class RecordingMediaWorkDirectoryService implements MediaWorkDirectoryService {

        private final Path workDirectory;
        private final List<String> createdJobIds = new ArrayList<>();
        private final List<Path> cleanedDirectories = new ArrayList<>();

        private RecordingMediaWorkDirectoryService(Path rootDirectory) {
            this.workDirectory = rootDirectory.resolve("media-work");
        }

        @Override
        public Path createJobWorkDirectory(String jobId) {
            createdJobIds.add(jobId);
            try {
                Files.createDirectories(workDirectory);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to create test work directory.", ex);
            }
            return workDirectory;
        }

        @Override
        public void deleteRecursively(Path directory) {
            cleanedDirectories.add(directory);
        }
    }

    private static class RecordingFfmpegAudioExtractionService implements FfmpegAudioExtractionService {

        private final ExtractedAudioBo result;
        private ExtractAudioCommand command;

        private RecordingFfmpegAudioExtractionService(ExtractedAudioBo result) {
            this.result = result;
        }

        @Override
        public ExtractedAudioBo extractAudio(ExtractAudioCommand command) {
            this.command = command;
            return result;
        }
    }
}
