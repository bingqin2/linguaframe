package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.exception.CostBudgetExceededException;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.TranscriptSubtitleExportPipelineStage;
import com.linguaframe.job.service.impl.TargetSubtitleExportPipelineStage;
import com.linguaframe.job.service.impl.QualityEvaluationPipelineStage;
import com.linguaframe.job.service.impl.DubbingAudioGenerationPipelineStage;
import com.linguaframe.job.service.impl.SubtitleBurnInPipelineStage;
import com.linguaframe.job.service.impl.WorkerSummaryArtifactPipelineStage;
import com.linguaframe.job.service.impl.AudioExtractionPipelineStage;
import com.linguaframe.job.service.impl.LocalizationJobExecutionServiceImpl;
import com.linguaframe.job.service.impl.WorkerSmokePipelineStage;
import com.linguaframe.job.service.impl.WorkerStageRouterImpl;
import com.linguaframe.media.domain.bo.BurnInSubtitlesCommand;
import com.linguaframe.media.domain.bo.BurnedVideoBo;
import com.linguaframe.media.domain.bo.ExtractAudioCommand;
import com.linguaframe.media.domain.bo.ExtractedAudioBo;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.FfmpegAudioExtractionService;
import com.linguaframe.media.service.FfmpegSubtitleBurnInService;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
    private LocalizationJobQueryService queryService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private com.linguaframe.common.config.LinguaFrameProperties properties;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
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
    void evictsCachedJobSnapshotDuringProcessingAndCompletion() {
        Instant now = Instant.parse("2026-06-27T06:10:00Z");
        createJob("execution-video-cache-evict", "execution-job-cache-evict", LocalizationJobStatus.QUEUED, now);
        RecordingStage stage = new RecordingStage(false);
        LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                properties,
                new WorkerStageRouterImpl(),
                message -> {
                },
                cacheService,
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        service.execute(message("execution-job-cache-evict", "execution-video-cache-evict", now));

        verify(cacheService, org.mockito.Mockito.atLeast(2)).evict("execution-job-cache-evict");
    }

    @Test
    void evictsCachedJobSnapshotWhenStageFails() {
        Instant now = Instant.parse("2026-06-27T06:15:00Z");
        createJob("execution-video-cache-fail", "execution-job-cache-fail", LocalizationJobStatus.QUEUED, now);
        RecordingStage stage = new RecordingStage(true);
        LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                properties,
                new WorkerStageRouterImpl(),
                message -> {
                },
                cacheService,
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        service.execute(message("execution-job-cache-fail", "execution-video-cache-fail", now));

        verify(cacheService, org.mockito.Mockito.atLeast(2)).evict("execution-job-cache-fail");
    }

    @Test
    void recordsCacheHitTimelineEventsReportedByStageContext() {
        Instant now = Instant.parse("2026-06-27T09:40:00Z");
        createJob("execution-video-cache-hit", "execution-job-cache-hit", LocalizationJobStatus.QUEUED, now);
        CacheHitStage stage = new CacheHitStage();
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-cache-hit", "execution-video-cache-hit", now));

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(timelineEventRepository.findByJobId("execution-job-cache-hit"))
                .extracting(event -> event.stage() + ":" + event.status() + ":" + event.message())
                .contains(
                        LocalizationJobStage.DUBBING_AUDIO_GENERATION
                                + ":" + JobTimelineEventStatus.CACHE_HIT
                                + ":Reused cached DUBBING_AUDIO artifact cached-dubbing-artifact."
                );
    }

    @Test
    void recordsProviderCacheHitTimelineEventsReportedByStageContext() {
        Instant now = Instant.parse("2026-06-27T09:45:00Z");
        createJob("execution-video-provider-cache-hit", "execution-job-provider-cache-hit", LocalizationJobStatus.QUEUED, now);
        ProviderCacheHitStage stage = new ProviderCacheHitStage();
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-provider-cache-hit", "execution-video-provider-cache-hit", now));

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(timelineEventRepository.findByJobId("execution-job-provider-cache-hit"))
                .extracting(event -> event.stage() + ":" + event.status() + ":" + event.message())
                .contains(
                        LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT
                                + ":" + JobTimelineEventStatus.CACHE_HIT
                                + ":Reused cached TRANSCRIPTION provider result from job source-provider-cache-job."
                );
        assertThat(queryService.getJob("execution-job-provider-cache-hit").cacheSummary().providerCacheHitCount())
                .isEqualTo(1);
    }

    @Test
    void ffmpegWorkerRunsInitialSegmentAndPublishesOpenAiHandoff() {
        Instant now = Instant.parse("2026-06-27T10:20:00Z");
        createJob("execution-video-ffmpeg-start", "execution-job-ffmpeg-start", LocalizationJobStatus.QUEUED, now);
        properties.getWorker().setRole(WorkerRole.FFMPEG);
        RecordingStage smokeStage = new RecordingStage(false, LocalizationJobStage.WORKER_SMOKE);
        RecordingStage audioStage = new RecordingStage(false, LocalizationJobStage.AUDIO_EXTRACTION);
        RecordingStage transcriptStage = new RecordingStage(false, LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        RecordingPublisher publisher = new RecordingPublisher();
        LocalizationJobExecutionService service = service(
                List.of(smokeStage, audioStage, transcriptStage),
                publisher,
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-ffmpeg-start", "execution-video-ffmpeg-start", now));

            assertThat(result.executed()).isTrue();
            assertThat(result.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
            assertThat(smokeStage.context).isNotNull();
            assertThat(audioStage.context).isNotNull();
            assertThat(transcriptStage.context).isNull();
            assertThat(publisher.messages).singleElement()
                    .satisfies(message -> assertThat(message.startStage())
                            .isEqualTo(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT));
            assertThat(jobRepository.findById("execution-job-ffmpeg-start"))
                    .get()
                    .extracting(LocalizationJobRecord::status)
                    .isEqualTo(LocalizationJobStatus.PROCESSING);
        } finally {
            properties.getWorker().setRole(WorkerRole.COMBINED);
        }
    }

    @Test
    void openAiWorkerRunsModelSegmentAndPublishesFfmpegHandoff() {
        Instant now = Instant.parse("2026-06-27T10:25:00Z");
        createJob("execution-video-openai", "execution-job-openai", LocalizationJobStatus.PROCESSING, now);
        properties.getWorker().setRole(WorkerRole.OPENAI);
        RecordingStage transcriptStage = new RecordingStage(false, LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        RecordingStage targetStage = new RecordingStage(false, LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        RecordingStage evaluationStage = new RecordingStage(false, LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION);
        RecordingStage ttsStage = new RecordingStage(false, LocalizationJobStage.DUBBING_AUDIO_GENERATION);
        RecordingStage burnInStage = new RecordingStage(false, LocalizationJobStage.SUBTITLE_BURN_IN);
        RecordingPublisher publisher = new RecordingPublisher();
        LocalizationJobExecutionService service = service(
                List.of(transcriptStage, targetStage, evaluationStage, ttsStage, burnInStage),
                publisher,
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message(
                    "execution-job-openai",
                    "execution-video-openai",
                    now,
                    LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT
            ));

            assertThat(result.executed()).isTrue();
            assertThat(result.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
            assertThat(transcriptStage.context).isNotNull();
            assertThat(targetStage.context).isNotNull();
            assertThat(evaluationStage.context).isNotNull();
            assertThat(ttsStage.context).isNotNull();
            assertThat(burnInStage.context).isNull();
            assertThat(publisher.messages).singleElement()
                    .satisfies(message -> assertThat(message.startStage()).isEqualTo(LocalizationJobStage.SUBTITLE_BURN_IN));
        } finally {
            properties.getWorker().setRole(WorkerRole.COMBINED);
        }
    }

    @Test
    void ffmpegWorkerRunsFinalSegmentAndMarksCompleted() {
        Instant now = Instant.parse("2026-06-27T10:30:00Z");
        createJob("execution-video-ffmpeg-final", "execution-job-ffmpeg-final", LocalizationJobStatus.PROCESSING, now);
        properties.getWorker().setRole(WorkerRole.FFMPEG);
        RecordingStage burnInStage = new RecordingStage(false, LocalizationJobStage.SUBTITLE_BURN_IN);
        RecordingStage summaryStage = new RecordingStage(false, LocalizationJobStage.ARTIFACT_SUMMARY);
        RecordingPublisher publisher = new RecordingPublisher();
        LocalizationJobExecutionService service = service(
                List.of(burnInStage, summaryStage),
                publisher,
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message(
                    "execution-job-ffmpeg-final",
                    "execution-video-ffmpeg-final",
                    now,
                    LocalizationJobStage.SUBTITLE_BURN_IN
            ));

            assertThat(result.executed()).isTrue();
            assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
            assertThat(burnInStage.context).isNotNull();
            assertThat(summaryStage.context).isNotNull();
            assertThat(publisher.messages).isEmpty();
            assertThat(jobRepository.findById("execution-job-ffmpeg-final"))
                    .get()
                    .extracting(LocalizationJobRecord::status)
                    .isEqualTo(LocalizationJobStatus.COMPLETED);
        } finally {
            properties.getWorker().setRole(WorkerRole.COMBINED);
        }
    }

    @Test
    void failsJobWhenWorkerReceivesUnownedStartStage() {
        Instant now = Instant.parse("2026-06-27T10:35:00Z");
        createJob("execution-video-unowned", "execution-job-unowned", LocalizationJobStatus.PROCESSING, now);
        properties.getWorker().setRole(WorkerRole.OPENAI);
        LocalizationJobExecutionService service = service(
                List.of(new RecordingStage(false, LocalizationJobStage.SUBTITLE_BURN_IN)),
                new RecordingPublisher(),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message(
                    "execution-job-unowned",
                    "execution-video-unowned",
                    now,
                    LocalizationJobStage.SUBTITLE_BURN_IN
            ));

            assertThat(result.executed()).isTrue();
            assertThat(result.status()).isEqualTo(LocalizationJobStatus.FAILED);
            assertThat(jobRepository.findById("execution-job-unowned"))
                    .get()
                    .satisfies(job -> {
                        assertThat(job.status()).isEqualTo(LocalizationJobStatus.FAILED);
                        assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.SUBTITLE_BURN_IN);
                        assertThat(job.failureReason()).contains("Worker role OPENAI cannot execute start stage SUBTITLE_BURN_IN.");
                    });
        } finally {
            properties.getWorker().setRole(WorkerRole.COMBINED);
        }
    }

    @Test
    void skipsHandoffSegmentWhenJobIsNotProcessing() {
        Instant now = Instant.parse("2026-06-27T10:40:00Z");
        createJob("execution-video-handoff-stale", "execution-job-handoff-stale", LocalizationJobStatus.QUEUED, now);
        properties.getWorker().setRole(WorkerRole.OPENAI);
        RecordingStage transcriptStage = new RecordingStage(false, LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        LocalizationJobExecutionService service = service(
                List.of(transcriptStage),
                new RecordingPublisher(),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message(
                    "execution-job-handoff-stale",
                    "execution-video-handoff-stale",
                    now,
                    LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT
            ));

            assertThat(result.executed()).isFalse();
            assertThat(result.status()).isEqualTo(LocalizationJobStatus.QUEUED);
            assertThat(transcriptStage.context).isNull();
            assertThat(timelineEventRepository.findByJobId("execution-job-handoff-stale"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SKIPPED);
        } finally {
            properties.getWorker().setRole(WorkerRole.COMBINED);
        }
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
    void skipsCancelledJobMessageWithoutExecutingStages() {
        Instant now = Instant.parse("2026-06-27T04:00:00Z");
        createJob("execution-video-cancelled", "execution-job-cancelled", LocalizationJobStatus.CANCELLED, now);
        RecordingStage stage = new RecordingStage(false);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(stage),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-cancelled", "execution-video-cancelled", now));

        assertThat(result.executed()).isFalse();
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
        assertThat(stage.context).isNull();
    }

    @Test
    void stopsBeforeNextStageWhenJobIsCancelledDuringProcessing() {
        Instant now = Instant.parse("2026-06-27T04:10:00Z");
        createJob("execution-video-cancel-mid", "execution-job-cancel-mid", LocalizationJobStatus.QUEUED, now);
        CancellingStage cancellingStage = new CancellingStage(jobRepository, now.plusSeconds(20));
        RecordingStage nextStage = new RecordingStage(false, LocalizationJobStage.ARTIFACT_SUMMARY);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(cancellingStage, nextStage),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-cancel-mid", "execution-video-cancel-mid", now));

        assertThat(result.executed()).isTrue();
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.CANCELLED);
        assertThat(cancellingStage.context).isNotNull();
        assertThat(nextStage.context).isNull();
        assertThat(timelineEventRepository.findByJobId("execution-job-cancel-mid"))
                .extracting(event -> event.status())
                .contains(JobTimelineEventStatus.SKIPPED);
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
                                artifactService,
                                new EmptyArtifactCacheService()
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

    @Test
    void audioExtractionStageReusesCachedArtifactBeforeOpeningSourceVideo(@TempDir Path tempDir) {
        Instant now = Instant.parse("2026-06-27T09:50:00Z");
        createJob("execution-video-audio-cache", "execution-job-audio-cache", LocalizationJobStatus.QUEUED, now);
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(new byte[] {1, 2, 3});
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", new byte[] {7, 8, 9})
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingArtifactCacheService cacheService = new RecordingArtifactCacheService(new JobArtifactVo(
                "cached-audio-artifact",
                "execution-job-audio-cache",
                JobArtifactType.EXTRACTED_AUDIO,
                "audio.wav",
                "audio/wav",
                321L,
                "cached-audio-hash",
                true,
                "source-audio-artifact",
                Instant.parse("2026-06-27T09:50:10Z")
        ));
        properties.getFfmpeg().setAudioEnabled(true);
        LocalizationJobExecutionService service = new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                List.of(new AudioExtractionPipelineStage(
                        properties,
                        objectStorageService,
                        workDirectoryService,
                        audioExtractionService,
                        artifactService,
                        cacheService
                )),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        var result = service.execute(message("execution-job-audio-cache", "execution-video-audio-cache", now));

        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(cacheService.requestedTypes).containsExactly(JobArtifactType.EXTRACTED_AUDIO);
        assertThat(objectStorageService.openedObjectKeys).isEmpty();
        assertThat(workDirectoryService.createdJobIds).isEmpty();
        assertThat(audioExtractionService.command).isNull();
        assertThat(artifactService.commands).isEmpty();
        assertThat(timelineEventRepository.findByJobId("execution-job-audio-cache"))
                .extracting(event -> event.stage() + ":" + event.status())
                .contains(LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.CACHE_HIT);
    }

    @Test
    void transcriptSubtitleStageCreatesArtifactsAfterAudioExtraction(@TempDir Path tempDir) throws IOException {
        Instant now = Instant.parse("2026-06-26T21:00:00Z");
        createJob("execution-video-8", "execution-job-8", LocalizationJobStatus.QUEUED, now);
        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] audioBytes = new byte[] {7, 8, 9};
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", audioBytes)
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();
        RecordingTranscriptService transcriptService = new RecordingTranscriptService();
        RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
        properties.getFfmpeg().setAudioEnabled(true);
        properties.getTranscription().setEnabled(true);
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
                        new TranscriptSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptionProvider,
                                transcriptService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-8", "execution-video-8", now));

            assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
            assertThat(timelineEventRepository.findByJobId("execution-job-8"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(
                            LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                    );
            assertThat(transcriptionProvider.jobId).isEqualTo("execution-job-8");
            assertThat(transcriptionProvider.audioContent).containsExactly(audioBytes);
            assertThat(transcriptService.jobId).isEqualTo("execution-job-8");
            assertThat(artifactService.commands)
                    .extracting(CreateJobArtifactCommand::type)
                    .containsExactly(
                            JobArtifactType.EXTRACTED_AUDIO,
                            JobArtifactType.TRANSCRIPT_JSON,
                            JobArtifactType.SUBTITLE_SRT,
                            JobArtifactType.SUBTITLE_VTT,
                            JobArtifactType.WORKER_SUMMARY
                    );
            assertThat(artifactService.commands.get(1).filename()).isEqualTo("transcript.json");
            assertThat(artifactService.commands.get(1).contentType()).isEqualTo("application/json");
            assertThat(new String(artifactService.commands.get(1).content(), StandardCharsets.UTF_8))
                    .isEqualTo("transcript-json");
            assertThat(artifactService.commands.get(2).filename()).isEqualTo("subtitles.srt");
            assertThat(artifactService.commands.get(2).contentType()).isEqualTo("application/x-subrip");
            assertThat(artifactService.commands.get(3).filename()).isEqualTo("subtitles.vtt");
            assertThat(artifactService.commands.get(3).contentType()).isEqualTo("text/vtt");
        } finally {
            properties.getFfmpeg().setAudioEnabled(false);
            properties.getTranscription().setEnabled(false);
        }
    }

    @Test
    void targetSubtitleStageCreatesArtifactsAfterTranscriptExport(@TempDir Path tempDir) throws IOException {
        Instant now = Instant.parse("2026-06-26T22:00:00Z");
        createJob("execution-video-9", "execution-job-9", LocalizationJobStatus.QUEUED, now);
        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] audioBytes = new byte[] {7, 8, 9};
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", audioBytes)
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();
        RecordingTranscriptService transcriptService = new RecordingTranscriptService();
        RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
        RecordingTranslationProvider translationProvider = new RecordingTranslationProvider();
        RecordingSubtitleService subtitleService = new RecordingSubtitleService();
        properties.getFfmpeg().setAudioEnabled(true);
        properties.getTranscription().setEnabled(true);
        properties.getTranslation().setEnabled(true);
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
                        new TranscriptSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptionProvider,
                                transcriptService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new TargetSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptService,
                                translationProvider,
                                subtitleService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-9", "execution-video-9", now));

            assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
            assertThat(timelineEventRepository.findByJobId("execution-job-9"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(
                            LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                    );
            assertThat(translationProvider.jobId).isEqualTo("execution-job-9");
            assertThat(translationProvider.targetLanguage).isEqualTo("zh-CN");
            assertThat(subtitleService.jobId).isEqualTo("execution-job-9");
            assertThat(subtitleService.language).isEqualTo("zh-CN");
            assertThat(artifactService.commands)
                    .extracting(CreateJobArtifactCommand::type)
                    .containsExactly(
                            JobArtifactType.EXTRACTED_AUDIO,
                            JobArtifactType.TRANSCRIPT_JSON,
                            JobArtifactType.SUBTITLE_SRT,
                            JobArtifactType.SUBTITLE_VTT,
                            JobArtifactType.TARGET_SUBTITLE_JSON,
                            JobArtifactType.TARGET_SUBTITLE_SRT,
                            JobArtifactType.TARGET_SUBTITLE_VTT,
                            JobArtifactType.WORKER_SUMMARY
                    );
            assertThat(artifactService.commands.get(4).filename()).isEqualTo("target-subtitles.json");
            assertThat(artifactService.commands.get(4).contentType()).isEqualTo("application/json");
            assertThat(artifactService.commands.get(5).filename()).isEqualTo("target-subtitles.srt");
            assertThat(artifactService.commands.get(5).contentType()).isEqualTo("application/x-subrip");
            assertThat(artifactService.commands.get(6).filename()).isEqualTo("target-subtitles.vtt");
            assertThat(artifactService.commands.get(6).contentType()).isEqualTo("text/vtt");
        } finally {
            properties.getFfmpeg().setAudioEnabled(false);
            properties.getTranscription().setEnabled(false);
            properties.getTranslation().setEnabled(false);
        }
    }

    @Test
    void dubbingAudioStageCreatesArtifactAfterTargetSubtitleExport(@TempDir Path tempDir) throws IOException {
        Instant now = Instant.parse("2026-06-26T23:00:00Z");
        createJob("execution-video-10", "execution-job-10", LocalizationJobStatus.QUEUED, now);
        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] audioBytes = new byte[] {7, 8, 9};
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", audioBytes)
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();
        RecordingTranscriptService transcriptService = new RecordingTranscriptService();
        RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
        RecordingTranslationProvider translationProvider = new RecordingTranslationProvider();
        RecordingSubtitleService subtitleService = new RecordingSubtitleService();
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        RecordingFfmpegSubtitleBurnInService burnInService = new RecordingFfmpegSubtitleBurnInService();
        properties.getFfmpeg().setAudioEnabled(true);
        properties.getFfmpeg().setBurnInEnabled(true);
        properties.getTranscription().setEnabled(true);
        properties.getTranslation().setEnabled(true);
        properties.getTts().setEnabled(true);
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
                        new TranscriptSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptionProvider,
                                transcriptService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new TargetSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptService,
                                translationProvider,
                                subtitleService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new DubbingAudioGenerationPipelineStage(
                                properties,
                                artifactService,
                                subtitleService,
                                ttsProvider,
                                new NoopCostBudgetGuardService()
                        ),
                        new SubtitleBurnInPipelineStage(
                                properties,
                                objectStorageService,
                                workDirectoryService,
                                burnInService,
                                subtitleService,
                                subtitleExportService,
                                artifactService
                        ),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-10", "execution-video-10", now));

            assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
            assertThat(timelineEventRepository.findByJobId("execution-job-10"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(
                            LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.DUBBING_AUDIO_GENERATION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.DUBBING_AUDIO_GENERATION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.SUBTITLE_BURN_IN + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.SUBTITLE_BURN_IN + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                    );
            assertThat(ttsProvider.request.jobId()).isEqualTo("execution-job-10");
            assertThat(ttsProvider.request.language()).isEqualTo("zh-CN");
            assertThat(ttsProvider.request.text()).isEqualTo("Translated first line\nTranslated second line");
            assertThat(artifactService.commands)
                    .extracting(CreateJobArtifactCommand::type)
                    .containsExactly(
                            JobArtifactType.EXTRACTED_AUDIO,
                            JobArtifactType.TRANSCRIPT_JSON,
                            JobArtifactType.SUBTITLE_SRT,
                            JobArtifactType.SUBTITLE_VTT,
                            JobArtifactType.TARGET_SUBTITLE_JSON,
                            JobArtifactType.TARGET_SUBTITLE_SRT,
                            JobArtifactType.TARGET_SUBTITLE_VTT,
                            JobArtifactType.DUBBING_AUDIO,
                            JobArtifactType.BURNED_VIDEO,
                            JobArtifactType.WORKER_SUMMARY
                    );
            CreateJobArtifactCommand command = artifactService.commands.get(7);
            assertThat(command.filename()).isEqualTo("dubbing-audio.mp3");
            assertThat(command.contentType()).isEqualTo("audio/mpeg");
            assertThat(command.content()).containsExactly(5, 4, 3);
            assertThat(burnInService.command.jobId()).isEqualTo("execution-job-10");
            assertThat(burnInService.command.inputVideoPath()).isEqualTo(workDirectoryService.workDirectory.resolve("source-video.mp4"));
            assertThat(burnInService.command.subtitlePath()).isEqualTo(workDirectoryService.workDirectory.resolve("target-subtitles.srt"));
            assertThat(burnInService.command.outputVideoPath()).isEqualTo(workDirectoryService.workDirectory.resolve("burned-video.mp4"));
            CreateJobArtifactCommand burnedVideoCommand = artifactService.commands.get(8);
            assertThat(burnedVideoCommand.filename()).isEqualTo("burned-video.mp4");
            assertThat(burnedVideoCommand.contentType()).isEqualTo("video/mp4");
            assertThat(burnedVideoCommand.content()).containsExactly(6, 5, 4);
        } finally {
            properties.getFfmpeg().setAudioEnabled(false);
            properties.getFfmpeg().setBurnInEnabled(false);
            properties.getTranscription().setEnabled(false);
            properties.getTranslation().setEnabled(false);
            properties.getTts().setEnabled(false);
        }
    }

    @Test
    void qualityEvaluationStageRunsAfterTargetSubtitleExportAndBeforeDubbing(@TempDir Path tempDir)
            throws IOException {
        Instant now = Instant.parse("2026-06-27T01:00:00Z");
        createJob("execution-video-11", "execution-job-11", LocalizationJobStatus.QUEUED, now);
        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] audioBytes = new byte[] {7, 8, 9};
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", audioBytes)
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();
        RecordingTranscriptService transcriptService = new RecordingTranscriptService();
        RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
        RecordingTranslationProvider translationProvider = new RecordingTranslationProvider();
        RecordingSubtitleService subtitleService = new RecordingSubtitleService();
        RecordingQualityEvaluationService qualityEvaluationService = new RecordingQualityEvaluationService();
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        properties.getFfmpeg().setAudioEnabled(true);
        properties.getTranscription().setEnabled(true);
        properties.getTranslation().setEnabled(true);
        properties.getEvaluation().setEnabled(true);
        properties.getTts().setEnabled(true);
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
                        new TranscriptSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptionProvider,
                                transcriptService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new TargetSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptService,
                                translationProvider,
                                subtitleService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new QualityEvaluationPipelineStage(
                                properties,
                                transcriptService,
                                subtitleService,
                                qualityEvaluationService,
                                new NoopCostBudgetGuardService()
                        ),
                        new DubbingAudioGenerationPipelineStage(
                                properties,
                                artifactService,
                                subtitleService,
                                ttsProvider,
                                new NoopCostBudgetGuardService()
                        ),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-11", "execution-video-11", now));

            assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
            assertThat(timelineEventRepository.findByJobId("execution-job-11"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(
                            LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.DUBBING_AUDIO_GENERATION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.DUBBING_AUDIO_GENERATION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.ARTIFACT_SUMMARY + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.COMPLETED + ":" + JobTimelineEventStatus.SUCCEEDED
                    );
            assertThat(qualityEvaluationService.jobId).isEqualTo("execution-job-11");
            assertThat(qualityEvaluationService.language).isEqualTo("zh-CN");
            assertThat(qualityEvaluationService.sourceSegments).hasSize(2);
            assertThat(qualityEvaluationService.targetSegments)
                    .extracting(SubtitleSegmentVo::text)
                    .containsExactly("Translated first line", "Translated second line");
            assertThat(ttsProvider.request.jobId()).isEqualTo("execution-job-11");
        } finally {
            properties.getFfmpeg().setAudioEnabled(false);
            properties.getTranscription().setEnabled(false);
            properties.getTranslation().setEnabled(false);
            properties.getEvaluation().setEnabled(false);
            properties.getTts().setEnabled(false);
        }
    }

    @Test
    void budgetGuardFailureMarksJobFailedBeforeNextAiProviderCall(@TempDir Path tempDir)
            throws IOException {
        Instant now = Instant.parse("2026-06-27T02:00:00Z");
        createJob("execution-video-budget", "execution-job-budget", LocalizationJobStatus.QUEUED, now);
        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] audioBytes = new byte[] {7, 8, 9};
        RecordingObjectStorageService objectStorageService = new RecordingObjectStorageService(sourceBytes);
        RecordingMediaWorkDirectoryService workDirectoryService = new RecordingMediaWorkDirectoryService(tempDir);
        RecordingFfmpegAudioExtractionService audioExtractionService = new RecordingFfmpegAudioExtractionService(
                new ExtractedAudioBo("audio.wav", "audio/wav", audioBytes)
        );
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        RecordingTranscriptionProvider transcriptionProvider = new RecordingTranscriptionProvider();
        RecordingTranscriptService transcriptService = new RecordingTranscriptService();
        RecordingSubtitleExportService subtitleExportService = new RecordingSubtitleExportService();
        RecordingTranslationProvider translationProvider = new RecordingTranslationProvider();
        RecordingSubtitleService subtitleService = new RecordingSubtitleService();
        RecordingTtsProvider ttsProvider = new RecordingTtsProvider();
        properties.getFfmpeg().setAudioEnabled(true);
        properties.getTranscription().setEnabled(true);
        properties.getTranslation().setEnabled(true);
        properties.getTts().setEnabled(true);
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
                        new TranscriptSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptionProvider,
                                transcriptService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new TargetSubtitleExportPipelineStage(
                                properties,
                                artifactService,
                                transcriptService,
                                translationProvider,
                                subtitleService,
                                subtitleExportService,
                                new NoopCostBudgetGuardService()
                        ),
                        new DubbingAudioGenerationPipelineStage(
                                properties,
                                artifactService,
                                subtitleService,
                                ttsProvider,
                                new FailingCostBudgetGuardService()
                        ),
                        new WorkerSummaryArtifactPipelineStage(artifactService, Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC))
                ),
                Clock.fixed(now.plusSeconds(10), ZoneOffset.UTC)
        );

        try {
            var result = service.execute(message("execution-job-budget", "execution-video-budget", now));

            assertThat(result.status()).isEqualTo(LocalizationJobStatus.FAILED);
            assertThat(ttsProvider.request).isNull();
            assertThat(jobRepository.findById("execution-job-budget"))
                    .get()
                    .satisfies(job -> {
                        assertThat(job.status()).isEqualTo(LocalizationJobStatus.FAILED);
                        assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION);
                        assertThat(job.failureReason()).contains("Job cost budget exceeded before DUBBING_AUDIO_GENERATION");
                    });
            assertThat(timelineEventRepository.findByJobId("execution-job-budget"))
                    .extracting(event -> event.stage() + ":" + event.status())
                    .containsExactly(
                            LocalizationJobStage.WORKER_RECEIVED + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.WORKER_SMOKE + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.AUDIO_EXTRACTION + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.TARGET_SUBTITLE_EXPORT + ":" + JobTimelineEventStatus.SUCCEEDED,
                            LocalizationJobStage.DUBBING_AUDIO_GENERATION + ":" + JobTimelineEventStatus.STARTED,
                            LocalizationJobStage.DUBBING_AUDIO_GENERATION + ":" + JobTimelineEventStatus.FAILED
                    );
            assertThat(artifactService.commands)
                    .extracting(CreateJobArtifactCommand::type)
                    .doesNotContain(JobArtifactType.DUBBING_AUDIO, JobArtifactType.WORKER_SUMMARY);
        } finally {
            properties.getFfmpeg().setAudioEnabled(false);
            properties.getTranscription().setEnabled(false);
            properties.getTranslation().setEnabled(false);
            properties.getTts().setEnabled(false);
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
        return message(jobId, videoId, createdAt, LocalizationJobStage.WORKER_SMOKE);
    }

    private QueuedLocalizationJobMessage message(
            String jobId,
            String videoId,
            Instant createdAt,
            LocalizationJobStage startStage
    ) {
        return new QueuedLocalizationJobMessage(
                jobId,
                videoId,
                "source-videos/" + videoId + "/sample.mp4",
                "zh-CN",
                createdAt,
                startStage
        );
    }

    private LocalizationJobExecutionService service(
            List<LocalizationPipelineStage> stages,
            JobQueuePublisher publisher,
            Clock clock
    ) {
        return new LocalizationJobExecutionServiceImpl(
                jobRepository,
                timelineEventRepository,
                stages,
                properties,
                new WorkerStageRouterImpl(),
                publisher,
                mock(LocalizationJobStatusCacheService.class),
                clock
        );
    }

    private static class RecordingStage implements LocalizationPipelineStage {

        private final boolean fail;
        private final LocalizationJobStage stage;
        private LocalizationJobExecutionContextBo context;

        private RecordingStage(boolean fail) {
            this(fail, LocalizationJobStage.WORKER_SMOKE);
        }

        private RecordingStage(boolean fail, LocalizationJobStage stage) {
            this.fail = fail;
            this.stage = stage;
        }

        @Override
        public LocalizationJobStage stage() {
            return stage;
        }

        @Override
        public void execute(LocalizationJobExecutionContextBo context) {
            this.context = context;
            if (fail) {
                throw new IllegalStateException("stage exploded");
            }
        }
    }

    private static class RecordingPublisher implements JobQueuePublisher {

        private final List<QueuedLocalizationJobMessage> messages = new ArrayList<>();

        @Override
        public void publish(QueuedLocalizationJobMessage message) {
            messages.add(message);
        }
    }

    private static class CacheHitStage implements LocalizationPipelineStage {

        @Override
        public LocalizationJobStage stage() {
            return LocalizationJobStage.DUBBING_AUDIO_GENERATION;
        }

        @Override
        public void execute(LocalizationJobExecutionContextBo context) {
            context.recordCacheHit(new JobArtifactVo(
                    "cached-dubbing-artifact",
                    context.job().id(),
                    JobArtifactType.DUBBING_AUDIO,
                    "dubbing-audio.mp3",
                    "audio/mpeg",
                    123L,
                    "cached-dubbing-hash",
                    true,
                    "source-dubbing-artifact",
                    Instant.parse("2026-06-27T09:40:10Z")
            ));
        }
    }

    private static class ProviderCacheHitStage implements LocalizationPipelineStage {

        @Override
        public LocalizationJobStage stage() {
            return LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT;
        }

        @Override
        public void execute(LocalizationJobExecutionContextBo context) {
            context.recordProviderCacheHit(new com.linguaframe.job.domain.vo.ProviderCacheHitVo(
                    ModelCallOperation.TRANSCRIPTION,
                    "provider-cache-key",
                    "source-provider-cache-job"
            ));
        }
    }

    private static class CancellingStage implements LocalizationPipelineStage {

        private final LocalizationJobRepository jobRepository;
        private final Instant cancelledAt;
        private LocalizationJobExecutionContextBo context;

        private CancellingStage(LocalizationJobRepository jobRepository, Instant cancelledAt) {
            this.jobRepository = jobRepository;
            this.cancelledAt = cancelledAt;
        }

        @Override
        public LocalizationJobStage stage() {
            return LocalizationJobStage.WORKER_SMOKE;
        }

        @Override
        public void execute(LocalizationJobExecutionContextBo context) {
            this.context = context;
            jobRepository.markCancelled(context.job().id(), cancelledAt);
        }
    }

    private static class NoopCostBudgetGuardService implements CostBudgetGuardService {

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
        }
    }

    private static class FailingCostBudgetGuardService implements CostBudgetGuardService {

        @Override
        public void assertWithinBudget(String jobId, LocalizationJobStage stage) {
            throw new CostBudgetExceededException(
                    "Job cost budget exceeded before " + stage
                            + ": current estimated cost 0.01 USD, limit 0.01 USD."
            );
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        private final List<CreateJobArtifactCommand> commands = new ArrayList<>();
        private final List<JobArtifactVo> artifacts = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            commands.add(command);
            JobArtifactVo artifact = new JobArtifactVo(
                    "recording-artifact-" + commands.size(),
                    command.jobId(),
                    command.type(),
                    command.filename(),
                    command.contentType(),
                    command.content().length,
                    "recording-hash-" + commands.size(),
                    false,
                    null,
                    Instant.parse("2026-06-26T19:00:10Z")
            );
            artifacts.add(artifact);
            return artifact;
        }

        @Override
        public JobArtifactVo createReusedArtifact(
                String jobId,
                com.linguaframe.job.domain.entity.JobArtifactRecord source
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts.stream()
                    .filter(artifact -> artifact.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            for (int i = 0; i < artifacts.size(); i++) {
                JobArtifactVo artifact = artifacts.get(i);
                if (artifact.jobId().equals(jobId) && artifact.artifactId().equals(artifactId)) {
                    return new com.linguaframe.job.domain.bo.StoredObjectResourceBo(
                            artifact.filename(),
                            artifact.contentType(),
                            artifact.sizeBytes(),
                            new ByteArrayInputStream(commands.get(i).content())
                    );
                }
            }
            throw new IllegalArgumentException("Artifact not found.");
        }
    }

    private static class EmptyArtifactCacheService implements ArtifactCacheService {

        @Override
        public java.util.Optional<JobArtifactVo> tryReuseArtifact(
                LocalizationJobExecutionContextBo context,
                JobArtifactType type
        ) {
            return java.util.Optional.empty();
        }
    }

    private static class RecordingArtifactCacheService implements ArtifactCacheService {

        private final JobArtifactVo artifact;
        private final List<JobArtifactType> requestedTypes = new ArrayList<>();

        private RecordingArtifactCacheService(JobArtifactVo artifact) {
            this.artifact = artifact;
        }

        @Override
        public java.util.Optional<JobArtifactVo> tryReuseArtifact(
                LocalizationJobExecutionContextBo context,
                JobArtifactType type
        ) {
            requestedTypes.add(type);
            return java.util.Optional.of(artifact);
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

        @Override
        public void delete(String objectKey) {
            throw new UnsupportedOperationException();
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

    private static class RecordingFfmpegSubtitleBurnInService implements FfmpegSubtitleBurnInService {

        private BurnInSubtitlesCommand command;

        @Override
        public BurnedVideoBo burnInSubtitles(BurnInSubtitlesCommand command) {
            this.command = command;
            return new BurnedVideoBo("burned-video.mp4", "video/mp4", new byte[] {6, 5, 4});
        }
    }

    private static class RecordingTranscriptionProvider implements TranscriptionProvider {

        private String jobId;
        private byte[] audioContent;

        @Override
        public TranscriptionResultBo transcribe(String jobId, byte[] audioContent) {
            this.jobId = jobId;
            this.audioContent = audioContent;
            return new TranscriptionResultBo(List.of(
                    new TranscriptionSegmentBo(0, 0L, 1_200L, "First line"),
                    new TranscriptionSegmentBo(1, 1_200L, 2_400L, "Second line")
            ));
        }
    }

    private static class RecordingTranscriptService implements TranscriptService {

        private String jobId;
        private List<TranscriptSegmentVo> segments = List.of(
                new TranscriptSegmentVo(0, 0L, 1_200L, "First line"),
                new TranscriptSegmentVo(1, 1_200L, 2_400L, "Second line")
        );

        @Override
        public List<TranscriptSegmentVo> replaceTranscript(String jobId, TranscriptionResultBo result) {
            this.jobId = jobId;
            return segments;
        }

        @Override
        public List<TranscriptSegmentVo> listTranscript(String jobId) {
            return segments;
        }
    }

    private static class RecordingSubtitleExportService implements SubtitleExportService {

        @Override
        public byte[] exportTranscriptJson(List<TranscriptSegmentVo> segments) {
            return "transcript-json".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSrt(List<TranscriptSegmentVo> segments) {
            return "subtitle-srt".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportVtt(List<TranscriptSegmentVo> segments) {
            return "subtitle-vtt".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleJson(List<SubtitleSegmentVo> segments) {
            return "target-subtitle-json".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleSrt(List<SubtitleSegmentVo> segments) {
            return "target-subtitle-srt".getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public byte[] exportSubtitleVtt(List<SubtitleSegmentVo> segments) {
            return "target-subtitle-vtt".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static class RecordingTranslationProvider implements TranslationProvider {

        private String jobId;
        private String targetLanguage;

        @Override
        public TranslationResultBo translate(
                String jobId,
                String targetLanguage,
                List<TranscriptSegmentVo> transcriptSegments
        ) {
            this.jobId = jobId;
            this.targetLanguage = targetLanguage;
            return new TranslationResultBo(List.of(
                    new TranslationSegmentBo(0, 0L, 1_200L, "Translated first line"),
                    new TranslationSegmentBo(1, 1_200L, 2_400L, "Translated second line")
            ));
        }
    }

    private static class RecordingSubtitleService implements SubtitleService {

        private String jobId;
        private String language;
        private List<SubtitleSegmentVo> subtitles = List.of();

        @Override
        public List<SubtitleSegmentVo> replaceSubtitles(String jobId, String language, TranslationResultBo result) {
            this.jobId = jobId;
            this.language = language;
            subtitles = List.of(
                    new SubtitleSegmentVo(language, 0, 0L, 1_200L, "Translated first line"),
                    new SubtitleSegmentVo(language, 1, 1_200L, 2_400L, "Translated second line")
            );
            return subtitles;
        }

        @Override
        public List<SubtitleSegmentVo> listSubtitles(String jobId, String language) {
            return subtitles;
        }
    }

    private static class RecordingTtsProvider implements TtsProvider {

        private TtsRequestBo request;

        @Override
        public TtsResultBo synthesize(TtsRequestBo request) {
            this.request = request;
            return new TtsResultBo(new byte[] {5, 4, 3}, "dubbing-audio.mp3", "audio/mpeg");
        }
    }

    private static class RecordingQualityEvaluationService implements QualityEvaluationService {

        private String jobId;
        private String language;
        private List<TranscriptSegmentVo> sourceSegments;
        private List<SubtitleSegmentVo> targetSegments;

        @Override
        public QualityEvaluationVo evaluateAndStore(
                String jobId,
                String language,
                List<TranscriptSegmentVo> sourceSegments,
                List<SubtitleSegmentVo> targetSegments
        ) {
            this.jobId = jobId;
            this.language = language;
            this.sourceSegments = sourceSegments;
            this.targetSegments = targetSegments;
            return new QualityEvaluationVo(
                    "recording-quality-evaluation",
                    jobId,
                    language,
                    92,
                    "GOOD",
                    95,
                    92,
                    94,
                    88,
                    List.of("No blocking issue."),
                    List.of("Review terminology."),
                    QualityEvaluationStatus.SUCCEEDED,
                    null,
                    Instant.parse("2026-06-27T01:00:10Z")
            );
        }

        @Override
        public java.util.Optional<QualityEvaluationVo> latestForJob(String jobId) {
            return java.util.Optional.empty();
        }
    }
}
