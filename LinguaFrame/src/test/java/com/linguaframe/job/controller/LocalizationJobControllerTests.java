package com.linguaframe.job.controller;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.media.domain.bo.AudioWaveformBo;
import com.linguaframe.media.domain.bo.AudioWaveformBucketBo;
import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.repository.QualityEvaluationRepository;
import com.linguaframe.job.domain.entity.QualityEvaluationRecord;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.TranscriptService;
import com.linguaframe.job.service.SubtitleService;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.domain.bo.DubbedVideoBo;
import com.linguaframe.media.repository.VideoRepository;
import com.linguaframe.media.service.FfmpegAudioReplacementService;
import com.linguaframe.media.service.FfmpegAudioWaveformService;
import com.linguaframe.media.service.FfmpegNarratedVideoMixService;
import com.linguaframe.media.service.FfmpegTimedAudioBedService;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "linguaframe.cost.translation-input-usd-per-million-tokens=0.15",
        "linguaframe.cost.translation-output-usd-per-million-tokens=0.60",
        "linguaframe.worker.max-retries=1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LocalizationJobControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobDispatchEventRepository dispatchEventRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

    @Autowired
    private TranscriptService transcriptService;

    @Autowired
    private SubtitleService subtitleService;

    @Autowired
    private ModelCallAuditService modelCallAuditService;

    @Autowired
    private QualityEvaluationRepository qualityEvaluationRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private ObjectStorageService objectStorageService;

    @MockitoBean
    private FfmpegAudioReplacementService ffmpegAudioReplacementService;

    @MockitoBean
    private FfmpegAudioWaveformService ffmpegAudioWaveformService;

    @MockitoBean
    private FfmpegNarratedVideoMixService ffmpegNarratedVideoMixService;

    @MockitoBean
    private FfmpegTimedAudioBedService ffmpegTimedAudioBedService;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM quality_evaluations").update();
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM narration_mix_settings").update();
        jdbcClient.sql("DELETE FROM narration_segments").update();
        jdbcClient.sql("DELETE FROM subtitle_draft_segments").update();
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
        when(objectStorageService.store(any(StoreObjectCommand.class))).thenAnswer(invocation -> {
            StoreObjectCommand command = invocation.getArgument(0);
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        });
        when(objectStorageService.open(anyString()))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(ffmpegAudioReplacementService.replaceAudio(any())).thenReturn(
                new DubbedVideoBo("narrated-video.mp4", "video/mp4", new byte[] {9, 9, 9})
        );
        when(ffmpegNarratedVideoMixService.mixNarration(any())).thenReturn(
                new DubbedVideoBo("narrated-video.mp4", "video/mp4", new byte[] {9, 9, 9})
        );
        when(ffmpegTimedAudioBedService.createAudioBed(any())).thenReturn(
                new TtsResultBo(new byte[] {1, 2, 3}, "narration-audio.mp3", "audio/mpeg")
        );
        when(ffmpegAudioWaveformService.analyze(any())).thenReturn(
                new AudioWaveformBo(
                        96,
                        new BigDecimal("120.000"),
                        List.of(new AudioWaveformBucketBo(
                                0,
                                new BigDecimal("0.000"),
                                new BigDecimal("1.250"),
                                new BigDecimal("0.750"),
                                new BigDecimal("0.500")
                        ))
                )
        );
    }

    @Test
    void listsLocalizationJobsOrderedNewestFirst() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:00:00Z");
        createJob("video-list-old", "job-controller-list-old", "old.mp4", LocalizationJobStatus.COMPLETED, base);
        createJob("video-list-new", "job-controller-list-new", "new.mp4", LocalizationJobStatus.PROCESSING, base.plusSeconds(20));
        createJob("video-list-middle", "job-controller-list-middle", "middle.mp4", LocalizationJobStatus.FAILED, base.plusSeconds(10));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-list-new",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=2, sourceChars=61",
                "segments=2, targetChars=29"
        ));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.jobs[0].jobId").value("job-controller-list-new"))
                .andExpect(jsonPath("$.jobs[0].videoId").value("video-list-new"))
                .andExpect(jsonPath("$.jobs[0].filename").value("new.mp4"))
                .andExpect(jsonPath("$.jobs[0].targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.jobs[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.jobs[0].estimatedCostUsd").value(0.00045000))
                .andExpect(jsonPath("$.jobs[1].jobId").value("job-controller-list-middle"))
                .andExpect(jsonPath("$.jobs[2].jobId").value("job-controller-list-old"));
    }

    @Test
    void listsLocalizationJobsFilteredByStatus() throws Exception {
        Instant base = Instant.parse("2026-06-27T01:30:00Z");
        createJob("video-filter-queued", "job-controller-list-filter-queued", "queued.mp4", LocalizationJobStatus.QUEUED, base);
        createJob("video-filter-failed", "job-controller-list-filter-failed", "failed.mp4", LocalizationJobStatus.FAILED, base.plusSeconds(10));

        mockMvc.perform(get("/api/jobs").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.jobs[0].jobId").value("job-controller-list-filter-failed"))
                .andExpect(jsonPath("$.jobs[0].status").value("FAILED"))
                .andExpect(jsonPath("$.jobs[0].filename").value("failed.mp4"));
    }

    @Test
    void rejectsInvalidJobListStatus() throws Exception {
        mockMvc.perform(get("/api/jobs").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void normalizesInvalidJobListLimitAndOffset() throws Exception {
        Instant base = Instant.parse("2026-06-27T02:00:00Z");
        createJob("video-list-normalized", "job-controller-list-normalized", "normalized.mp4", LocalizationJobStatus.QUEUED, base);

        mockMvc.perform(get("/api/jobs")
                        .param("limit", "999")
                        .param("offset", "-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(20))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.jobs[0].jobId").value("job-controller-list-normalized"));
    }

    @Test
    void returnsQueuedLocalizationJobWithDispatchState() throws Exception {
        Instant createdAt = Instant.parse("2026-06-25T15:00:00Z");
        videoRepository.save(new VideoRecord(
                "job-controller-video",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/job-controller-video/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-job",
                "job-controller-video",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
        dispatchEventRepository.save(new JobDispatchEventRecord(
                "job-controller-dispatch-event",
                "job-controller-job",
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{\"jobId\":\"job-controller-job\"}",
                JobDispatchEventStatus.PENDING,
                0,
                createdAt,
                null,
                null,
                createdAt,
                createdAt
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.dispatchStatus").value("PENDING"))
                .andExpect(jsonPath("$.dispatchAttempts").value(0))
                .andExpect(jsonPath("$.dispatchedAt").doesNotExist())
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.timelineEvents").isArray())
                .andExpect(jsonPath("$.timelineEvents").isEmpty());
    }

    @Test
    void opensJobProgressEventStream() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T08:00:00Z");
        createJob("job-controller-events-video", "job-controller-events-job", "events.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);

        mockMvc.perform(get("/api/jobs/{jobId}/events", "job-controller-events-job"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/event-stream")));
    }

    @Test
    void returnsLocalizationJobWithUsageSummaryAndModelCalls() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T18:00:00Z");
        createJob("job-controller-video-usage", "job-controller-job-usage", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-generated-artifact",
                "job-controller-job-usage",
                JobArtifactType.EXTRACTED_AUDIO,
                "job-artifacts/job-controller-job-usage/generated/audio.wav",
                "audio.wav",
                "audio/wav",
                10L,
                "generated-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-reused-artifact",
                "job-controller-job-usage",
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/source-job/source-artifact/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                20L,
                "reused-hash",
                true,
                "source-artifact",
                createdAt.plusSeconds(2)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-provider-cache-hit",
                "job-controller-job-usage",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.CACHE_HIT,
                "Reused cached TRANSLATION provider result from job source-provider-cache-job.",
                null,
                null,
                createdAt.plusSeconds(3)
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-job-usage",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=2, sourceChars=61",
                "segments=2, targetChars=29"
        ));
        modelCallAuditService.recordFailure(new CreateModelCallRecordCommand(
                "job-controller-job-usage",
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                ModelCallOperation.TTS,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-tts",
                "openai-tts-v1",
                75L,
                null,
                null,
                null,
                null,
                "characters=17",
                null
        ), "OpenAI TTS request failed with status 401");

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job-usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usageSummary.modelCallCount").value(2))
                .andExpect(jsonPath("$.usageSummary.failedModelCallCount").value(1))
                .andExpect(jsonPath("$.usageSummary.totalLatencyMs").value(200))
                .andExpect(jsonPath("$.usageSummary.estimatedCostUsd").value(0.00045000))
                .andExpect(jsonPath("$.usageSummary.inputTokens").value(1000))
                .andExpect(jsonPath("$.usageSummary.outputTokens").value(500))
                .andExpect(jsonPath("$.cacheSummary.cacheHitCount").value(1))
                .andExpect(jsonPath("$.cacheSummary.generatedArtifactCount").value(1))
                .andExpect(jsonPath("$.cacheSummary.providerCacheHitCount").value(1))
                .andExpect(jsonPath("$.modelCalls[0].operation").value("TRANSLATION"))
                .andExpect(jsonPath("$.modelCalls[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.modelCalls[0].estimatedCostUsd").value(0.00045000))
                .andExpect(jsonPath("$.modelCalls[0].inputSummary").value("target=zh-CN, segments=2, sourceChars=61"))
                .andExpect(jsonPath("$.modelCalls[0].outputSummary").value("segments=2, targetChars=29"))
                .andExpect(jsonPath("$.modelCalls[1].operation").value("TTS"))
                .andExpect(jsonPath("$.modelCalls[1].status").value("FAILED"))
                .andExpect(jsonPath("$.modelCalls[1].inputSummary").value("characters=17"))
                .andExpect(jsonPath("$.modelCalls[1].outputSummary").doesNotExist())
                .andExpect(jsonPath("$.modelCalls[1].safeErrorSummary")
                        .value("OpenAI TTS request failed with status 401"));
    }

    @Test
    void returnsLocalizationJobWithLatestQualityEvaluation() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:00:00Z");
        createJob("job-controller-video-quality", "job-controller-job-quality", createdAt);
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "quality-controller-old",
                "job-controller-job-quality",
                "zh-CN",
                80,
                "NEEDS_REVIEW",
                80,
                79,
                78,
                77,
                List.of("Earlier issue."),
                List.of("Earlier fix."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(1)
        ));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "quality-controller-new",
                "job-controller-job-quality",
                "zh-CN",
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
                createdAt.plusSeconds(2)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job-quality"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qualityEvaluation.evaluationId").value("quality-controller-new"))
                .andExpect(jsonPath("$.qualityEvaluation.jobId").value("job-controller-job-quality"))
                .andExpect(jsonPath("$.qualityEvaluation.language").value("zh-CN"))
                .andExpect(jsonPath("$.qualityEvaluation.score").value(92))
                .andExpect(jsonPath("$.qualityEvaluation.verdict").value("GOOD"))
                .andExpect(jsonPath("$.qualityEvaluation.completeness").value(95))
                .andExpect(jsonPath("$.qualityEvaluation.readability").value(92))
                .andExpect(jsonPath("$.qualityEvaluation.timingPreservation").value(94))
                .andExpect(jsonPath("$.qualityEvaluation.naturalness").value(88))
                .andExpect(jsonPath("$.qualityEvaluation.issues[0]").value("No blocking issue."))
                .andExpect(jsonPath("$.qualityEvaluation.suggestedFixes[0]").value("Review terminology."))
                .andExpect(jsonPath("$.qualityEvaluation.status").value("SUCCEEDED"));
    }

    @Test
    void downloadsQualityEvaluationEvidenceMarkdown() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:10:00Z");
        videoRepository.save(new VideoRecord(
                "job-video-quality-evidence",
                "quality-evidence.mp4",
                "video/mp4",
                123L,
                "source-videos/job-video-quality-evidence/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-job-quality-evidence",
                "job-video-quality-evidence",
                "zh-CN",
                "verse",
                LocalizationJobStatus.COMPLETED,
                createdAt,
                createdAt.plusSeconds(1),
                createdAt.plusSeconds(20),
                null,
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                "provider request payload raw transcript text sk-test /Users/example/job-artifacts/raw.json",
                0,
                createdAt.plusSeconds(20)
        ));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "quality-controller-evidence",
                "job-controller-job-quality-evidence",
                "zh-CN",
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
                createdAt.plusSeconds(2)
        ));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download",
                        "job-controller-job-quality-evidence"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-job-quality-evidence-quality-evidence.md\""
                ))
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown)
                .contains("# LinguaFrame Quality Evaluation Evidence")
                .contains("- Job: job-controller-job-quality-evidence")
                .contains("- Video: job-video-quality-evidence")
                .contains("- Target language: zh-CN")
                .contains("- Status: SUCCEEDED")
                .contains("- Score: 92 / 100")
                .contains("- Verdict: GOOD")
                .contains("- Completeness: 95 / 100")
                .contains("- Issue count: 1")
                .contains("- No blocking issue.")
                .contains("- Suggested fix count: 1")
                .contains("- Review terminology.")
                .contains("/api/jobs/job-controller-job-quality-evidence/diagnostics/download")
                .contains("/api/jobs/job-controller-job-quality-evidence/subtitle-review?language=zh-CN")
                .doesNotContain("raw transcript text")
                .doesNotContain("provider request payload")
                .doesNotContain("/Users/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("sk-test");
    }

    @Test
    void downloadsDemoRunPackageForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:20:00Z");
        videoRepository.save(new VideoRecord(
                "job-video-demo-run-package",
                "demo-run-package.mp4",
                "video/mp4",
                123L,
                "source-videos/job-video-demo-run-package/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-demo-run-package",
                "job-video-demo-run-package",
                "zh-CN",
                "verse",
                LocalizationJobStatus.COMPLETED,
                createdAt,
                createdAt.plusSeconds(1),
                createdAt.plusSeconds(20),
                null,
                null,
                "provider request payload raw transcript text raw subtitle text sk-test /Users/example/job-artifacts/raw.json",
                0,
                createdAt.plusSeconds(20)
        ));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "quality-controller-demo-run-package",
                "job-controller-demo-run-package",
                "zh-CN",
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
                createdAt.plusSeconds(2)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "demo-run-reviewed-json",
                "job-controller-demo-run-package",
                JobArtifactType.REVIEWED_SUBTITLE_JSON,
                "job-artifacts/job-controller-demo-run-package/reviewed-json/reviewed-subtitles.zh-CN.json",
                "reviewed-subtitles.zh-CN.json",
                "application/json",
                15L,
                "reviewed-json-hash",
                false,
                null,
                createdAt.plusSeconds(3)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "demo-run-reviewed-srt",
                "job-controller-demo-run-package",
                JobArtifactType.REVIEWED_SUBTITLE_SRT,
                "job-artifacts/job-controller-demo-run-package/reviewed-srt/reviewed-subtitles.zh-CN.srt",
                "reviewed-subtitles.zh-CN.srt",
                "application/x-subrip",
                48L,
                "reviewed-srt-hash",
                false,
                null,
                createdAt.plusSeconds(4)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "demo-run-reviewed-vtt",
                "job-controller-demo-run-package",
                JobArtifactType.REVIEWED_SUBTITLE_VTT,
                "job-artifacts/job-controller-demo-run-package/reviewed-vtt/reviewed-subtitles.zh-CN.vtt",
                "reviewed-subtitles.zh-CN.vtt",
                "text/vtt",
                42L,
                "reviewed-vtt-hash",
                false,
                null,
                createdAt.plusSeconds(5)
        ));

        byte[] body = mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-run-package/download",
                        "job-controller-demo-run-package"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-demo-run-package-demo-run-package.zip\""
                ))
                .andExpect(content().contentType("application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(body);
        assertThat(entries)
                .containsKeys(
                        "manifest.json",
                        "README.md",
                        "job-detail.json",
                        "diagnostics.json",
                        "evidence.md",
                        "quality-evidence.md",
                        "delivery-manifest.md",
                        "demo-handoff-checklist.md",
                        "demo-session-report.md"
                );
        assertThat(entries.get("README.md"))
                .contains("# LinguaFrame Demo Run Package")
                .contains("/api/jobs/job-controller-demo-run-package/demo-run-package/download");
        assertThat(entries.get("demo-session-report.md"))
                .contains("- Overall: READY")
                .contains("- Quality: 92 / 100, GOOD, SUCCEEDED");
        assertThat(String.join("\n", entries.values()))
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("provider request payload")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("sk-test");
    }

    @Test
    void downloadsAiAuditPackageForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:40:00Z");
        videoRepository.save(new VideoRecord(
                "job-video-ai-audit-package",
                "ai-audit-package.mp4",
                "video/mp4",
                123L,
                "source-videos/job-video-ai-audit-package/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-ai-audit-package",
                "job-video-ai-audit-package",
                "zh-CN",
                "verse",
                LocalizationJobStatus.COMPLETED,
                createdAt,
                createdAt.plusSeconds(1),
                createdAt.plusSeconds(20),
                null,
                null,
                "provider request payload raw transcript text raw subtitle text sk-test /Users/example/job-artifacts/raw.json",
                0,
                createdAt.plusSeconds(20)
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-ai-audit-package",
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-transcribe",
                "openai-audio-transcriptions-v1",
                250L,
                null,
                null,
                new BigDecimal("32.5"),
                null,
                "audioSeconds=32.5",
                "segments=8"
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-ai-audit-package",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4.1-mini",
                "openai-subtitle-translation-v1",
                550L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=8",
                "translatedSegmentCount=8"
        ));
        modelCallAuditService.recordFailure(new CreateModelCallRecordCommand(
                "job-controller-ai-audit-package",
                LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                ModelCallOperation.EVALUATION,
                ModelCallProvider.OPENAI,
                "gpt-4.1-mini",
                "openai-translation-quality-evaluation-v1",
                700L,
                900,
                300,
                null,
                null,
                "provider request payload raw transcript text",
                "sk-test /Users/example/job-artifacts/raw.json"
        ), "provider request payload OPENAI_API_KEY");

        byte[] body = mockMvc.perform(get(
                        "/api/jobs/{jobId}/ai-audit-package/download",
                        "job-controller-ai-audit-package"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-ai-audit-package-ai-audit-package.zip\""
                ))
                .andExpect(content().contentType("application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(body);
        assertThat(entries)
                .containsKeys(
                        "manifest.json",
                        "README.md",
                        "model-calls.json",
                        "prompt-templates.json",
                        "ai-usage-summary.json",
                        "ai-audit-report.md"
                );
        assertThat(entries.get("README.md"))
                .contains("# LinguaFrame AI Audit Package")
                .contains("/api/jobs/job-controller-ai-audit-package/ai-audit-package/download");
        assertThat(entries.get("model-calls.json"))
                .contains("openai-audio-transcriptions-v1")
                .contains("openai-subtitle-translation-v1")
                .contains("openai-translation-quality-evaluation-v1")
                .contains("omitted because it contained fields outside the AI audit safety contract");
        assertThat(entries.get("ai-audit-report.md"))
                .contains("- Model calls: 3")
                .contains("- Failed model calls: 1");
        assertThat(String.join("\n", entries.values()))
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("provider request payload")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("sk-test");
    }

    @Test
    void returnsOpenAiSmokeProofForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:45:00Z");
        createJob("job-video-openai-smoke-proof", "job-controller-openai-smoke-proof", "openai-smoke.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-openai-smoke-proof",
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-transcribe",
                "openai-audio-transcriptions-v1",
                250L,
                null,
                null,
                new BigDecimal("30.0"),
                null,
                "audioSeconds=30",
                "segments=4"
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-openai-smoke-proof",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4.1-mini",
                "openai-subtitle-translation-v1",
                550L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=4",
                "translatedSegmentCount=4"
        ));
        qualityEvaluationRepository.save(quality("quality-openai-smoke-proof", "job-controller-openai-smoke-proof", 91, createdAt.plusSeconds(20)));
        saveSmokeProofArtifact("smoke-proof-transcript-json", "job-controller-openai-smoke-proof", JobArtifactType.TRANSCRIPT_JSON);
        saveSmokeProofArtifact("smoke-proof-target-json", "job-controller-openai-smoke-proof", JobArtifactType.TARGET_SUBTITLE_JSON);
        saveSmokeProofArtifact("smoke-proof-target-srt", "job-controller-openai-smoke-proof", JobArtifactType.TARGET_SUBTITLE_SRT);
        saveSmokeProofArtifact("smoke-proof-target-vtt", "job-controller-openai-smoke-proof", JobArtifactType.TARGET_SUBTITLE_VTT);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/openai-smoke-proof",
                        "job-controller-openai-smoke-proof"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-openai-smoke-proof"))
                .andExpect(jsonPath("$.overallStatus").value("ATTENTION"))
                .andExpect(jsonPath("$.phase").value("OPENAI_SMOKE_NEEDS_REVIEW"))
                .andExpect(jsonPath("$.requiredChecks[?(@.name == 'OpenAI transcription call')].status").value("READY"))
                .andExpect(jsonPath("$.requiredChecks[?(@.name == 'OpenAI translation call')].status").value("READY"))
                .andExpect(jsonPath("$.modelCalls[?(@.operation == 'TRANSCRIPTION')].provider").value("OPENAI"))
                .andExpect(jsonPath("$.artifacts[?(@.type == 'TARGET_SUBTITLE_SRT')].filename").value("target-subtitles.zh-CN.srt"))
                .andExpect(jsonPath("$.safeLinks[?(@.href == '/api/jobs/job-controller-openai-smoke-proof/ai-audit-package/download')].label").value("AI audit package"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsOpenAiSmokeProofMarkdownForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:50:00Z");
        createJob("job-video-openai-smoke-proof-md", "job-controller-openai-smoke-proof-md", "openai-smoke-md.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-openai-smoke-proof-md",
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-transcribe",
                "openai-audio-transcriptions-v1",
                250L,
                null,
                null,
                new BigDecimal("30.0"),
                null,
                "audioSeconds=30",
                "segments=4"
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-openai-smoke-proof-md",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4.1-mini",
                "openai-subtitle-translation-v1",
                550L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=4",
                "translatedSegmentCount=4"
        ));
        saveSmokeProofArtifact("smoke-proof-md-transcript-json", "job-controller-openai-smoke-proof-md", JobArtifactType.TRANSCRIPT_JSON);
        saveSmokeProofArtifact("smoke-proof-md-target-json", "job-controller-openai-smoke-proof-md", JobArtifactType.TARGET_SUBTITLE_JSON);
        saveSmokeProofArtifact("smoke-proof-md-target-srt", "job-controller-openai-smoke-proof-md", JobArtifactType.TARGET_SUBTITLE_SRT);
        saveSmokeProofArtifact("smoke-proof-md-target-vtt", "job-controller-openai-smoke-proof-md", JobArtifactType.TARGET_SUBTITLE_VTT);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/openai-smoke-proof/markdown/download",
                        "job-controller-openai-smoke-proof-md"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-openai-smoke-proof-md-openai-smoke-proof.md\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# LinguaFrame OpenAI Smoke Proof")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("- Overall status: ATTENTION")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OpenAI transcription call")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/jobs/job-controller-openai-smoke-proof-md/openai-smoke-proof/markdown/download")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void returnsDemoReviewerWorkspaceForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:55:00Z");
        createReviewerWorkspaceJob("job-controller-reviewer", "job-video-reviewer", createdAt);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-reviewer-workspace",
                        "job-controller-reviewer"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-reviewer"))
                .andExpect(jsonPath("$.overallStatus").value("READY"))
                .andExpect(jsonPath("$.phase").value("REVIEW_PACKAGE_READY"))
                .andExpect(jsonPath("$.checks[?(@.key == 'ACCEPTANCE_GATE')].status").value("READY"))
                .andExpect(jsonPath("$.checks[?(@.key == 'OPENAI_SMOKE_PROOF')].status").value("READY"))
                .andExpect(jsonPath("$.safeLinks[?(@.href == '/api/jobs/job-controller-reviewer/demo-reviewer-workspace/download')].label").value("Reviewer workspace ZIP"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoReviewerWorkspaceMarkdownForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:57:00Z");
        createReviewerWorkspaceJob("job-controller-reviewer-md", "job-video-reviewer-md", createdAt);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-reviewer-workspace/markdown/download",
                        "job-controller-reviewer-md"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-reviewer-md-demo-reviewer-workspace.md\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# LinguaFrame Demo Reviewer Workspace")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("- Overall status: READY")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Demo run package")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoReviewerWorkspaceZipForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T02:59:00Z");
        createReviewerWorkspaceJob("job-controller-reviewer-zip", "job-video-reviewer-zip", createdAt);

        byte[] body = mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-reviewer-workspace/download",
                        "job-controller-reviewer-zip"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"linguaframe-job-job-controller-reviewer-zip-demo-reviewer-workspace.zip\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(body);
        assertThat(entries).containsKeys("manifest.json", "reviewer-workspace.md", "README.md");
        assertThat(entries.get("manifest.json")).contains("\"jobId\":\"job-controller-reviewer-zip\"");
        assertThat(entries.get("reviewer-workspace.md"))
                .contains("# LinguaFrame Demo Reviewer Workspace")
                .contains("Demo run package");
        assertThat(String.join("\n", entries.values()))
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("/Users/example")
                .doesNotContain("job-artifacts/")
                .doesNotContain("sk-test")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token");
    }

    @Test
    void returnsDemoHandoffPortalForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T03:01:00Z");
        createReviewerWorkspaceJob("job-controller-portal", "job-video-portal", createdAt);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-handoff-portal",
                        "job-controller-portal"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-portal"))
                .andExpect(jsonPath("$.overallStatus").value("READY"))
                .andExpect(jsonPath("$.phase").value("HANDOFF_PORTAL_READY"))
                .andExpect(jsonPath("$.checks[?(@.key == 'PORTAL_PACKAGE')].status").value("READY"))
                .andExpect(jsonPath("$.checks[?(@.key == 'REVIEWER_WORKSPACE')].status").value("READY"))
                .andExpect(jsonPath("$.safeLinks[?(@.href == '/api/jobs/job-controller-portal/demo-handoff-portal/download')].label").value("Demo handoff portal ZIP"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoHandoffPortalMarkdownForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T03:03:00Z");
        createReviewerWorkspaceJob("job-controller-portal-md", "job-video-portal-md", createdAt);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-handoff-portal/markdown/download",
                        "job-controller-portal-md"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-portal-md-demo-handoff-portal.md\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# LinguaFrame Demo Handoff Portal")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("- Overall status: READY")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Static handoff portal ZIP")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoHandoffPortalZipForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T03:05:00Z");
        createReviewerWorkspaceJob("job-controller-portal-zip", "job-video-portal-zip", createdAt);

        byte[] body = mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-handoff-portal/download",
                        "job-controller-portal-zip"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"linguaframe-job-job-controller-portal-zip-demo-handoff-portal.zip\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(body);
        assertThat(entries).containsKeys(
                "index.html",
                "manifest.json",
                "handoff-portal.md",
                "reviewer-workspace.json",
                "README.md",
                "acceptance-gate.json",
                "completion-certificate.json",
                "share-sheet.json",
                "run-monitor.json"
        );
        assertThat(entries.get("index.html"))
                .contains("LinguaFrame Demo Handoff Portal")
                .contains("HANDOFF_PORTAL_READY");
        assertThat(entries.get("handoff-portal.md"))
                .contains("# LinguaFrame Demo Handoff Portal")
                .contains("Demo reviewer workspace");
        assertThat(String.join("\n", entries.values()))
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("/Users/example")
                .doesNotContain("job-artifacts/")
                .doesNotContain("sk-test")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("bearer token");
    }

    @Test
    void returnsQueuedLocalizationJobWithoutDispatchEvent() throws Exception {
        Instant createdAt = Instant.parse("2026-06-25T16:00:00Z");
        videoRepository.save(new VideoRecord(
                "job-controller-video-no-dispatch",
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/job-controller-video-no-dispatch/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-job-no-dispatch",
                "job-controller-video-no-dispatch",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job-no-dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-no-dispatch"))
                .andExpect(jsonPath("$.dispatchStatus").doesNotExist())
                .andExpect(jsonPath("$.dispatchAttempts").value(0))
                .andExpect(jsonPath("$.dispatchedAt").doesNotExist())
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.timelineEvents").isArray())
                .andExpect(jsonPath("$.timelineEvents").isEmpty());
    }

    @Test
    void returnsFailedLocalizationJobWithFailureStateAndTimelineEvents() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T19:00:00Z");
        createJob("job-controller-video-failed", "job-controller-job-failed", createdAt);
        jobRepository.claimForExecution("job-controller-job-failed", createdAt.plusSeconds(1));
        jobRepository.markFailed(
                "job-controller-job-failed",
                LocalizationJobStage.WORKER_SMOKE,
                "stage failed safely",
                createdAt.plusSeconds(2)
        );
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-timeline-2",
                "job-controller-job-failed",
                LocalizationJobStage.WORKER_SMOKE,
                JobTimelineEventStatus.FAILED,
                "Smoke stage failed.",
                10L,
                "stage failed safely",
                createdAt.plusSeconds(2)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-timeline-1",
                "job-controller-job-failed",
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.STARTED,
                "Worker received localization job.",
                null,
                null,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}", "job-controller-job-failed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureStage").value("WORKER_SMOKE"))
                .andExpect(jsonPath("$.failureReason").value("stage failed safely"))
                .andExpect(jsonPath("$.failedAt").exists())
                .andExpect(jsonPath("$.retryCount").value(0))
                .andExpect(jsonPath("$.timelineEvents[0].stage").value("WORKER_RECEIVED"))
                .andExpect(jsonPath("$.timelineEvents[0].status").value("STARTED"))
                .andExpect(jsonPath("$.timelineEvents[1].stage").value("WORKER_SMOKE"))
                .andExpect(jsonPath("$.timelineEvents[1].status").value("FAILED"))
                .andExpect(jsonPath("$.timelineEvents[1].errorSummary").value("stage failed safely"));
    }

    @Test
    void retriesFailedLocalizationJobAndCreatesPendingDispatchEvent() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T20:00:00Z");
        createJob("job-controller-video-retry", "job-controller-job-retry", createdAt);
        jobRepository.claimForExecution("job-controller-job-retry", createdAt.plusSeconds(1));
        jobRepository.markFailed(
                "job-controller-job-retry",
                LocalizationJobStage.WORKER_SMOKE,
                "first execution failed",
                createdAt.plusSeconds(2)
        );

        mockMvc.perform(post("/api/jobs/{jobId}/retry", "job-controller-job-retry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-retry"))
                .andExpect(jsonPath("$.status").value("RETRYING"))
                .andExpect(jsonPath("$.retryCount").value(1))
                .andExpect(jsonPath("$.failureStage").doesNotExist())
                .andExpect(jsonPath("$.failureReason").doesNotExist());

        dispatchEventRepository.findLatestByJobId("job-controller-job-retry")
                .ifPresentOrElse(
                        event -> {
                            org.assertj.core.api.Assertions.assertThat(event.status()).isEqualTo(JobDispatchEventStatus.PENDING);
                            org.assertj.core.api.Assertions.assertThat(event.payloadJson()).contains("job-controller-job-retry");
                        },
                        () -> org.assertj.core.api.Assertions.fail("Expected retry dispatch event.")
                );
    }

    @Test
    void cancelsQueuedLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T03:40:00Z");
        createJob("job-controller-cancel-video", "job-controller-cancel-job", createdAt);

        mockMvc.perform(post("/api/jobs/{jobId}/cancel", "job-controller-cancel-job"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-cancel-job"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.completedAt").exists())
                .andExpect(jsonPath("$.timelineEvents[0].message").value("Cancellation requested."));
    }

    @Test
    void rejectsCompletedLocalizationJobCancellation() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T03:50:00Z");
        createJob(
                "job-cancel-completed-video",
                "job-controller-cancel-completed",
                "done.mp4",
                LocalizationJobStatus.COMPLETED,
                createdAt
        );

        mockMvc.perform(post("/api/jobs/{jobId}/cancel", "job-controller-cancel-completed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void listsArtifactsForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T22:00:00Z");
        createJob("job-controller-video-artifact", "job-controller-job-artifact", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-artifact-1",
                "job-controller-job-artifact",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-artifact/job-controller-artifact-1/worker-summary.json",
                "worker-summary.json",
                "application/json",
                42L,
                "abc123",
                true,
                "job-controller-source-artifact",
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}/artifacts", "job-controller-job-artifact"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].artifactId").value("job-controller-artifact-1"))
                .andExpect(jsonPath("$[0].jobId").value("job-controller-job-artifact"))
                .andExpect(jsonPath("$[0].type").value("WORKER_SUMMARY"))
                .andExpect(jsonPath("$[0].filename").value("worker-summary.json"))
                .andExpect(jsonPath("$[0].contentType").value("application/json"))
                .andExpect(jsonPath("$[0].sizeBytes").value(42))
                .andExpect(jsonPath("$[0].contentSha256").value("abc123"))
                .andExpect(jsonPath("$[0].cacheHit").value(true))
                .andExpect(jsonPath("$[0].sourceArtifactId").value("job-controller-source-artifact"))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    @Test
    void downloadsArtifactForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T23:00:00Z");
        createJob("job-controller-video-download", "job-controller-job-download", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-artifact-download",
                "job-controller-job-download",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-download/job-controller-artifact-download/worker-summary.json",
                "worker-summary.json",
                "application/json",
                11L,
                "download-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        when(objectStorageService.open("job-artifacts/job-controller-job-download/job-controller-artifact-download/worker-summary.json"))
                .thenReturn(new ByteArrayInputStream("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "job-controller-job-download",
                        "job-controller-artifact-download"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"worker-summary.json\""))
                .andExpect(header().longValue("Content-Length", 11L))
                .andExpect(content().contentType("application/json"))
                .andExpect(content().string("{\"ok\":true}"));
    }

    @Test
    void downloadsArtifactArchiveForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T09:45:00Z");
        createJob("job-controller-video-archive", "job-controller-job-archive", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-archive-summary",
                "job-controller-job-archive",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-archive/job-controller-archive-summary/worker-summary.json",
                "worker-summary.json",
                "application/json",
                11L,
                "summary-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-archive-vtt",
                "job-controller-job-archive",
                JobArtifactType.TARGET_SUBTITLE_VTT,
                "job-artifacts/job-controller-job-archive/job-controller-archive-vtt/target-subtitles.vtt",
                "target-subtitles.vtt",
                "text/vtt",
                6L,
                "vtt-hash",
                false,
                null,
                createdAt.plusSeconds(2)
        ));
        when(objectStorageService.open("job-artifacts/job-controller-job-archive/job-controller-archive-summary/worker-summary.json"))
                .thenReturn(new ByteArrayInputStream("{\"ok\":true}".getBytes(StandardCharsets.UTF_8)));
        when(objectStorageService.open("job-artifacts/job-controller-job-archive/job-controller-archive-vtt/target-subtitles.vtt"))
                .thenReturn(new ByteArrayInputStream("WEBVTT".getBytes(StandardCharsets.UTF_8)));

        byte[] zipBytes = mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/archive/download",
                        "job-controller-job-archive"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-job-archive-artifacts.zip\""
                ))
                .andExpect(content().contentType("application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(zipBytes);
        org.assertj.core.api.Assertions.assertThat(entries)
                .containsKeys(
                        "manifest.json",
                        "artifacts/WORKER_SUMMARY/job-controller-archive-summary-worker-summary.json",
                        "artifacts/TARGET_SUBTITLE_VTT/job-controller-archive-vtt-target-subtitles.vtt"
                );
        org.assertj.core.api.Assertions.assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-controller-job-archive\"")
                .contains("\"artifactCount\":2");
    }

    @Test
    void downloadsJobDiagnosticsReport() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T10:15:00Z");
        createJob(
                "job-controller-video-diagnostics",
                "job-controller-job-diagnostics",
                "diagnostics.mp4",
                LocalizationJobStatus.COMPLETED,
                createdAt
        );
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-diagnostics-artifact",
                "job-controller-job-diagnostics",
                JobArtifactType.WORKER_SUMMARY,
                "/Users/wangbingqin/private/job-artifacts/job-controller-job-diagnostics/summary.json",
                "worker-summary.json",
                "application/json",
                64L,
                "diagnostics-summary-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-diagnostics-timeline",
                "job-controller-job-diagnostics",
                LocalizationJobStage.ARTIFACT_SUMMARY,
                JobTimelineEventStatus.SUCCEEDED,
                "Created worker summary artifact",
                40L,
                null,
                createdAt.plusSeconds(2)
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-job-diagnostics",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=2, sourceChars=61",
                "segments=2, targetChars=29"
        ));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "job-controller-diagnostics-quality",
                "job-controller-job-diagnostics",
                "zh-CN",
                90,
                "PASS",
                92,
                91,
                88,
                89,
                List.of("Minor timing drift"),
                List.of("Review final caption"),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(3)
        ));

        String json = mockMvc.perform(get(
                        "/api/jobs/{jobId}/diagnostics/download",
                        "job-controller-job-diagnostics"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-job-diagnostics-diagnostics.json\""
                ))
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.job.jobId").value("job-controller-job-diagnostics"))
                .andExpect(jsonPath("$.job.status").value("COMPLETED"))
                .andExpect(jsonPath("$.artifactCount").value(1))
                .andExpect(jsonPath("$.artifacts[0].artifactId").value("job-controller-diagnostics-artifact"))
                .andExpect(jsonPath("$.artifacts[0].contentSha256").value("diagnostics-summary-hash"))
                .andExpect(jsonPath("$.job.timelineEvents[0].id").value("job-controller-diagnostics-timeline"))
                .andExpect(jsonPath("$.job.modelCalls[0].modelCallId").exists())
                .andExpect(jsonPath("$.job.qualityEvaluation.score").value(90))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(json)
                .doesNotContain("/Users/wangbingqin")
                .doesNotContain("private/job-artifacts")
                .doesNotContain("demo-access-token")
                .doesNotContain("sk-")
                .doesNotContain("\"objectKey\"");
    }

    @Test
    void downloadsJobEvidenceMarkdownReport() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T11:15:00Z");
        createJob(
                "job-controller-video-evidence",
                "job-controller-job-evidence",
                "evidence.mp4",
                LocalizationJobStatus.COMPLETED,
                createdAt
        );
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-evidence-artifact",
                "job-controller-job-evidence",
                JobArtifactType.WORKER_SUMMARY,
                "/Users/wangbingqin/private/job-artifacts/job-controller-job-evidence/summary.json",
                "worker-summary.json",
                "application/json",
                64L,
                "evidence-summary-hash-1234567890",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-evidence-timeline",
                "job-controller-job-evidence",
                LocalizationJobStage.ARTIFACT_SUMMARY,
                JobTimelineEventStatus.SUCCEEDED,
                "Created worker summary artifact",
                40L,
                null,
                createdAt.plusSeconds(2)
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-job-evidence",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=2, sourceChars=61",
                "segments=2, targetChars=29"
        ));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "job-controller-evidence-quality",
                "job-controller-job-evidence",
                "zh-CN",
                90,
                "PASS",
                92,
                91,
                88,
                89,
                List.of("Minor timing drift"),
                List.of("Review final caption"),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(3)
        ));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/evidence/markdown/download",
                        "job-controller-job-evidence"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-job-evidence-evidence.md\""
                ))
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(markdown)
                .contains("# LinguaFrame Demo Evidence")
                .contains("- Job: job-controller-job-evidence")
                .contains("- Video: job-controller-video-evidence")
                .contains("- Target language: zh-CN")
                .contains("- Status: COMPLETED")
                .contains("- Model calls: 1")
                .contains("- Estimated cost:")
                .contains("- Quality: 90 / 100, PASS, SUCCEEDED")
                .contains("- ARTIFACT_SUMMARY: SUCCEEDED")
                .contains("- WORKER_SUMMARY: worker-summary.json, 64 B, evidence-summary, Generated")
                .contains("- Result bundle: /api/jobs/job-controller-job-evidence/artifacts/archive/download")
                .contains("- Diagnostics: /api/jobs/job-controller-job-evidence/diagnostics/download")
                .doesNotContain("/Users/wangbingqin")
                .doesNotContain("private/job-artifacts")
                .doesNotContain("demo-access-token")
                .doesNotContain("sk-")
                .doesNotContain("\"objectKey\"");
    }

    @Test
    void downloadsJobEvidenceBundle() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T12:15:00Z");
        createJob(
                "job-controller-video-evidence-bundle",
                "job-controller-job-evidence-bundle",
                "evidence-bundle.mp4",
                LocalizationJobStatus.COMPLETED,
                createdAt
        );
        artifactRepository.save(new JobArtifactRecord(
                "job-evidence-bundle-artifact",
                "job-controller-job-evidence-bundle",
                JobArtifactType.WORKER_SUMMARY,
                "/Users/wangbingqin/private/job-artifacts/job-controller-job-evidence-bundle/summary.json",
                "worker-summary.json",
                "application/json",
                64L,
                "evidence-bundle-summary-hash-1234567890",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-evidence-bundle-timeline",
                "job-controller-job-evidence-bundle",
                LocalizationJobStage.ARTIFACT_SUMMARY,
                JobTimelineEventStatus.SUCCEEDED,
                "Created worker summary artifact",
                40L,
                null,
                createdAt.plusSeconds(2)
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                "job-controller-job-evidence-bundle",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                125L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=2, sourceChars=61",
                "segments=2, targetChars=29"
        ));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "job-evidence-bundle-quality",
                "job-controller-job-evidence-bundle",
                "zh-CN",
                90,
                "PASS",
                92,
                91,
                88,
                89,
                List.of("Minor timing drift"),
                List.of("Review final caption"),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(3)
        ));

        byte[] zipBytes = mockMvc.perform(get(
                        "/api/jobs/{jobId}/evidence/bundle/download",
                        "job-controller-job-evidence-bundle"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-job-evidence-bundle-evidence.zip\""
                ))
                .andExpect(content().contentType("application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(zipBytes);
        org.assertj.core.api.Assertions.assertThat(entries)
                .containsKeys("manifest.json", "evidence.md", "diagnostics.json");
        org.assertj.core.api.Assertions.assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-controller-job-evidence-bundle\"")
                .contains("\"videoId\":\"job-controller-video-evidence-bundle\"")
                .contains("\"status\":\"COMPLETED\"")
                .contains("\"artifactCount\":1")
                .contains("\"evidence.md\"")
                .contains("\"diagnostics.json\"");
        org.assertj.core.api.Assertions.assertThat(entries.get("evidence.md"))
                .contains("# LinguaFrame Demo Evidence")
                .contains("- Job: job-controller-job-evidence-bundle");
        org.assertj.core.api.Assertions.assertThat(entries.get("diagnostics.json"))
                .contains("\"jobId\":\"job-controller-job-evidence-bundle\"")
                .contains("\"artifactCount\":1");
        String combined = String.join("\n", entries.values());
        org.assertj.core.api.Assertions.assertThat(combined)
                .doesNotContain("/Users/")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("objectKey")
                .doesNotContain("demo-access-token")
                .doesNotContain("sk-")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("provider request payload");
    }

    @Test
    void downloadsReviewedHandoffPackageForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T12:45:00Z");
        createJob(
                "job-controller-video-handoff-package",
                "job-controller-job-handoff-package",
                "handoff-package.mp4",
                LocalizationJobStatus.COMPLETED,
                createdAt
        );
        List<JobArtifactRecord> artifacts = List.of(
                new JobArtifactRecord(
                        "job-controller-reviewed-json",
                        "job-controller-job-handoff-package",
                        JobArtifactType.REVIEWED_SUBTITLE_JSON,
                        "job-artifacts/job-controller-job-handoff-package/reviewed-json/reviewed-subtitles.zh-CN.json",
                        "reviewed-subtitles.zh-CN.json",
                        "application/json",
                        15L,
                        "reviewed-json-hash",
                        false,
                        null,
                        createdAt.plusSeconds(1)
                ),
                new JobArtifactRecord(
                        "job-controller-reviewed-srt",
                        "job-controller-job-handoff-package",
                        JobArtifactType.REVIEWED_SUBTITLE_SRT,
                        "job-artifacts/job-controller-job-handoff-package/reviewed-srt/reviewed-subtitles.zh-CN.srt",
                        "reviewed-subtitles.zh-CN.srt",
                        "application/x-subrip",
                        48L,
                        "reviewed-srt-hash",
                        false,
                        null,
                        createdAt.plusSeconds(2)
                ),
                new JobArtifactRecord(
                        "job-controller-reviewed-vtt",
                        "job-controller-job-handoff-package",
                        JobArtifactType.REVIEWED_SUBTITLE_VTT,
                        "job-artifacts/job-controller-job-handoff-package/reviewed-vtt/reviewed-subtitles.zh-CN.vtt",
                        "reviewed-subtitles.zh-CN.vtt",
                        "text/vtt",
                        42L,
                        "reviewed-vtt-hash",
                        false,
                        null,
                        createdAt.plusSeconds(3)
                ),
                new JobArtifactRecord(
                        "job-controller-reviewed-video",
                        "job-controller-job-handoff-package",
                        JobArtifactType.REVIEWED_BURNED_VIDEO,
                        "job-artifacts/job-controller-job-handoff-package/reviewed-video/reviewed-burned-video.mp4",
                        "reviewed-burned-video.mp4",
                        "video/mp4",
                        20L,
                        "reviewed-video-hash",
                        false,
                        null,
                        createdAt.plusSeconds(4)
                ),
                new JobArtifactRecord(
                        "job-controller-target-json",
                        "job-controller-job-handoff-package",
                        JobArtifactType.TARGET_SUBTITLE_JSON,
                        "job-artifacts/job-controller-job-handoff-package/target-json/target-subtitles.json",
                        "target-subtitles.json",
                        "application/json",
                        22L,
                        "target-json-hash",
                        false,
                        null,
                        createdAt.plusSeconds(5)
                )
        );
        artifacts.forEach(artifactRepository::save);
        when(objectStorageService.open("job-artifacts/job-controller-job-handoff-package/reviewed-json/reviewed-subtitles.zh-CN.json"))
                .thenReturn(new ByteArrayInputStream("{\"segments\":[]}".getBytes(StandardCharsets.UTF_8)));
        when(objectStorageService.open("job-artifacts/job-controller-job-handoff-package/reviewed-srt/reviewed-subtitles.zh-CN.srt"))
                .thenReturn(new ByteArrayInputStream("1\n00:00:00,000 --> 00:00:01,000\nReviewed line\n".getBytes(StandardCharsets.UTF_8)));
        when(objectStorageService.open("job-artifacts/job-controller-job-handoff-package/reviewed-vtt/reviewed-subtitles.zh-CN.vtt"))
                .thenReturn(new ByteArrayInputStream("WEBVTT\n\n00:00.000 --> 00:01.000\nReviewed line\n".getBytes(StandardCharsets.UTF_8)));
        when(objectStorageService.open("job-artifacts/job-controller-job-handoff-package/reviewed-video/reviewed-burned-video.mp4"))
                .thenReturn(new ByteArrayInputStream("reviewed-video-bytes".getBytes(StandardCharsets.UTF_8)));

        byte[] zipBytes = mockMvc.perform(get(
                        "/api/jobs/{jobId}/handoff-package/download",
                        "job-controller-job-handoff-package"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\"linguaframe-job-job-controller-job-handoff-package-handoff-package.zip\""
                ))
                .andExpect(content().contentType("application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(zipBytes);
        assertThat(entries)
                .containsKeys(
                        "manifest.json",
                        "delivery-manifest.md",
                        "evidence.md",
                        "diagnostics.json",
                        "reviewed/REVIEWED_SUBTITLE_JSON/job-controller-reviewed-json-reviewed-subtitles.zh-CN.json",
                        "reviewed/REVIEWED_SUBTITLE_SRT/job-controller-reviewed-srt-reviewed-subtitles.zh-CN.srt",
                        "reviewed/REVIEWED_SUBTITLE_VTT/job-controller-reviewed-vtt-reviewed-subtitles.zh-CN.vtt",
                        "reviewed/REVIEWED_BURNED_VIDEO/job-controller-reviewed-video-reviewed-burned-video.mp4"
                )
                .doesNotContainKey("reviewed/TARGET_SUBTITLE_JSON/job-controller-target-json-target-subtitles.json");
        assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-controller-job-handoff-package\"")
                .contains("\"handoffReady\":true")
                .contains("\"reviewedArtifactCount\":4");
        String combined = String.join("\n", entries.values());
        assertThat(combined)
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("objectKey")
                .doesNotContain("provider payload")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw generated subtitle");
    }

    @Test
    void returnsTranscriptSegmentsForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T00:00:00Z");
        createJob("job-controller-video-transcript", "job-controller-job-transcript", createdAt);
        transcriptService.replaceTranscript("job-controller-job-transcript", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_200L, "First line"),
                new TranscriptionSegmentBo(1, 1_200L, 2_400L, "Second line")
        )));

        mockMvc.perform(get("/api/jobs/{jobId}/transcript", "job-controller-job-transcript"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].startMs").value(0))
                .andExpect(jsonPath("$[0].endMs").value(1200))
                .andExpect(jsonPath("$[0].text").value("First line"))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].text").value("Second line"));
    }

    @Test
    void returnsTargetSubtitleSegmentsForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T00:30:00Z");
        createJob("job-controller-video-subtitle", "job-controller-job-subtitle", createdAt);
        subtitleService.replaceSubtitles("job-controller-job-subtitle", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_800L, "LinguaFrame 向你问好。"),
                new TranslationSegmentBo(1, 1_800L, 3_600L, "这个演示字幕是确定性的。")
        )));

        mockMvc.perform(get("/api/jobs/{jobId}/subtitles/{language}", "job-controller-job-subtitle", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].language").value("zh-CN"))
                .andExpect(jsonPath("$[0].index").value(0))
                .andExpect(jsonPath("$[0].startMs").value(0))
                .andExpect(jsonPath("$[0].endMs").value(1800))
                .andExpect(jsonPath("$[0].text").value("LinguaFrame 向你问好。"))
                .andExpect(jsonPath("$[1].index").value(1))
                .andExpect(jsonPath("$[1].text").value("这个演示字幕是确定性的。"));
    }

    @Test
    void returnsSubtitleReviewSummaryForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T00:45:00Z");
        createJob("job-controller-video-review", "job-controller-job-review", createdAt);
        transcriptService.replaceTranscript("job-controller-job-review", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_000L, "First line"),
                new TranscriptionSegmentBo(1, 1_000L, 2_000L, "Second line"),
                new TranscriptionSegmentBo(2, 2_000L, 3_000L, "Missing target")
        )));
        subtitleService.replaceSubtitles("job-controller-job-review", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行"),
                new TranslationSegmentBo(1, 1_400L, 2_400L, "第二行")
        )));
        qualityEvaluationRepository.save(new QualityEvaluationRecord(
                "job-controller-review-quality",
                "job-controller-job-review",
                "zh-CN",
                88,
                "NEEDS_REVIEW",
                90,
                85,
                70,
                86,
                List.of("Second line timing drift."),
                List.of("Adjust subtitle timing."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(3)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-review-target-json",
                "job-controller-job-review",
                JobArtifactType.TARGET_SUBTITLE_JSON,
                "job-artifacts/job-controller-job-review/target-subtitles.json",
                "target-subtitles.json",
                "application/json",
                10L,
                "review-json-hash",
                false,
                null,
                createdAt.plusSeconds(4)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-review-target-vtt",
                "job-controller-job-review",
                JobArtifactType.TARGET_SUBTITLE_VTT,
                "job-artifacts/job-controller-job-review/target-subtitles.vtt",
                "target-subtitles.vtt",
                "text/vtt",
                10L,
                "review-vtt-hash",
                false,
                null,
                createdAt.plusSeconds(5)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-review-video",
                "job-controller-job-review",
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/job-controller-job-review/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                20L,
                "review-video-hash",
                false,
                null,
                createdAt.plusSeconds(6)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}/subtitle-review", "job-controller-job-review")
                        .param("language", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-review"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.segmentCount").value(3))
                .andExpect(jsonPath("$.missingTargetCount").value(1))
                .andExpect(jsonPath("$.timingMismatchCount").value(1))
                .andExpect(jsonPath("$.averageDurationMs").value(1000))
                .andExpect(jsonPath("$.maxDurationMs").value(1000))
                .andExpect(jsonPath("$.qualityScore").value(88))
                .andExpect(jsonPath("$.qualityVerdict").value("NEEDS_REVIEW"))
                .andExpect(jsonPath("$.qualityIssueCount").value(1))
                .andExpect(jsonPath("$.qualitySuggestedFixCount").value(1))
                .andExpect(jsonPath("$.downloadableSubtitleArtifactCount").value(2))
                .andExpect(jsonPath("$.segments[0].index").value(0))
                .andExpect(jsonPath("$.segments[0].sourceText").value("First line"))
                .andExpect(jsonPath("$.segments[0].targetText").value("第一行"))
                .andExpect(jsonPath("$.segments[0].timingDeltaMs").value(0))
                .andExpect(jsonPath("$.segments[0].status").value("ALIGNED"))
                .andExpect(jsonPath("$.segments[1].timingDeltaMs").value(400))
                .andExpect(jsonPath("$.segments[1].status").value("TIMING_MISMATCH"))
                .andExpect(jsonPath("$.segments[2].targetText").doesNotExist())
                .andExpect(jsonPath("$.segments[2].status").value("MISSING_TARGET"));
    }

    @Test
    void updatesAndExportsSubtitleDraftForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:00:00Z");
        createJob("job-controller-video-draft", "job-controller-job-draft", createdAt);
        transcriptService.replaceTranscript("job-controller-job-draft", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_000L, "First source line"),
                new TranscriptionSegmentBo(1, 1_200L, 2_800L, "Second source line")
        )));
        subtitleService.replaceSubtitles("job-controller-job-draft", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行"),
                new TranslationSegmentBo(1, 1_200L, 2_800L, "第二行")
        )));

        mockMvc.perform(put("/api/jobs/{jobId}/subtitle-draft", "job-controller-job-draft")
                        .param("language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 1,
                                      "text": "修正后的第二行",
                                      "decision": "EDITED",
                                      "issueCategories": ["TERM", "READABILITY"],
                                      "reviewerNote": "controller note must stay out of evidence"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-draft"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.editedSegmentCount").value(1))
                .andExpect(jsonPath("$.segments[0].sourceText").value("First source line"))
                .andExpect(jsonPath("$.segments[0].generatedText").value("第一行"))
                .andExpect(jsonPath("$.segments[0].draftText").value("第一行"))
                .andExpect(jsonPath("$.segments[0].edited").value(false))
                .andExpect(jsonPath("$.segments[1].sourceText").value("Second source line"))
                .andExpect(jsonPath("$.segments[1].generatedText").value("第二行"))
                .andExpect(jsonPath("$.segments[1].draftText").value("修正后的第二行"))
                .andExpect(jsonPath("$.segments[1].edited").value(true))
                .andExpect(jsonPath("$.segments[1].decision").value("EDITED"))
                .andExpect(jsonPath("$.segments[1].issueCategories[0]").value("TERM"))
                .andExpect(jsonPath("$.segments[1].reviewerNote").value("controller note must stay out of evidence"))
                .andExpect(jsonPath("$.segments[1].noteLength").value(41));

        mockMvc.perform(get("/api/jobs/{jobId}/subtitle-draft/export", "job-controller-job-draft")
                        .param("language", "zh-CN")
                        .param("format", "srt"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"corrected-subtitles.zh-CN.srt\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("00:00:01,200 --> 00:00:02,800")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("修正后的第二行")));

        mockMvc.perform(get("/api/jobs/{jobId}/subtitle-draft/export", "job-controller-job-draft")
                        .param("language", "zh-CN")
                        .param("format", "json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"corrected-subtitles.zh-CN.json\"")))
                .andExpect(jsonPath("$.language").value("zh-CN"))
                .andExpect(jsonPath("$.segments[1].text").value("修正后的第二行"));

        mockMvc.perform(get("/api/jobs/{jobId}/subtitles/{language}", "job-controller-job-draft", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].text").value("第二行"));

        mockMvc.perform(delete("/api/jobs/{jobId}/subtitle-draft", "job-controller-job-draft")
                        .param("language", "zh-CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.editedSegmentCount").value(0))
                .andExpect(jsonPath("$.segments[1].draftText").value("第二行"))
                .andExpect(jsonPath("$.segments[1].edited").value(false))
                .andExpect(jsonPath("$.segments[1].decision").value("UNREVIEWED"));
    }

    @Test
    void managesNarrationWorkspaceForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:10:00Z");
        createJob("job-controller-video-narration", "job-controller-job-narration", createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-narration")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice",
                                      "duckingVolume": 0.250,
                                      "narrationVolume": 1.500,
                                      "fadeDurationMs": 125
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ],
                                  "mixKeyframes": [
                                    {
                                      "lane": "DUCKING_VOLUME",
                                      "timeSeconds": 0.000,
                                      "value": 0.600
                                    },
                                    {
                                      "lane": "DUCKING_VOLUME",
                                      "timeSeconds": 20.000,
                                      "value": 0.250
                                    },
                                    {
                                      "lane": "NARRATION_VOLUME",
                                      "timeSeconds": 20.000,
                                      "value": 1.400
                                    },
                                    {
                                      "lane": "FADE_DURATION_MS",
                                      "timeSeconds": 20.000,
                                      "value": 500.000
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-narration"))
                .andExpect(jsonPath("$.status").value("DRAFT_READY"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.totalDurationSeconds").value(28.500))
                .andExpect(jsonPath("$.totalCharacterCount").value(49))
                .andExpect(jsonPath("$.generationReady").value(true))
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.350))
                .andExpect(jsonPath("$.mixSettings.narrationVolume").value(1.000))
                .andExpect(jsonPath("$.mixSettings.fadeDurationMs").value(250))
                .andExpect(jsonPath("$.mixAutomation.keyframeCount").value(4))
                .andExpect(jsonPath("$.mixAutomation.duckingKeyframeCount").value(2))
                .andExpect(jsonPath("$.mixAutomation.narrationKeyframeCount").value(1))
                .andExpect(jsonPath("$.mixAutomation.fadeKeyframeCount").value(1))
                .andExpect(jsonPath("$.mixAutomation.keyframes[0].lane").value("DUCKING_VOLUME"))
                .andExpect(jsonPath("$.mixAutomation.keyframes[0].timeSeconds").value(0.000))
                .andExpect(jsonPath("$.mixAutomation.keyframes[0].value").value(0.600))
                .andExpect(jsonPath("$.mixAutomation.keyframes[3].lane").value("FADE_DURATION_MS"))
                .andExpect(jsonPath("$.mixAutomation.keyframes[3].value").value(500.000))
                .andExpect(jsonPath("$.timeline.startSeconds").value(15.000))
                .andExpect(jsonPath("$.timeline.endSeconds").value(70.500))
                .andExpect(jsonPath("$.timeline.totalSpanSeconds").value(55.500))
                .andExpect(jsonPath("$.timeline.coveredSeconds").value(28.500))
                .andExpect(jsonPath("$.timeline.gapSeconds").value(27.000))
                .andExpect(jsonPath("$.timeline.gapCount").value(1))
                .andExpect(jsonPath("$.timeline.hasOverlap").value(false))
                .andExpect(jsonPath("$.timeline.segments[0].leftPercent").value(0.00))
                .andExpect(jsonPath("$.timeline.segments[1].leftPercent").value(72.07))
                .andExpect(jsonPath("$.segments[0].index").value(0))
                .andExpect(jsonPath("$.segments[0].startSeconds").value(15.000))
                .andExpect(jsonPath("$.segments[0].endSeconds").value(28.000))
                .andExpect(jsonPath("$.segments[0].text").value("Explain the first scene."))
                .andExpect(jsonPath("$.segments[0].voice").value("demo-voice"))
                .andExpect(jsonPath("$.segments[0].duckingVolume").value(0.250))
                .andExpect(jsonPath("$.segments[0].narrationVolume").value(1.500))
                .andExpect(jsonPath("$.segments[0].fadeDurationMs").value(125))
                .andExpect(jsonPath("$.segments[1].duckingVolume").doesNotExist())
                .andExpect(jsonPath("$.segments[1].narrationVolume").doesNotExist())
                .andExpect(jsonPath("$.segments[1].fadeDurationMs").doesNotExist())
                .andExpect(jsonPath("$.segments[1].durationSeconds").value(15.500));

        mockMvc.perform(get("/api/jobs/{jobId}/narration-workspace", "job-controller-job-narration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT_READY"))
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.350))
                .andExpect(jsonPath("$.mixAutomation.keyframeCount").value(4))
                .andExpect(jsonPath("$.mixAutomation.keyframes[1].timeSeconds").value(20.000))
                .andExpect(jsonPath("$.timeline.gapCount").value(1))
                .andExpect(jsonPath("$.segments[0].duckingVolume").value(0.250))
                .andExpect(jsonPath("$.segments[0].narrationVolume").value(1.500))
                .andExpect(jsonPath("$.segments[0].fadeDurationMs").value(125))
                .andExpect(jsonPath("$.segments[1].text").value("Explain the second scene."));

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace/mix-settings", "job-controller-job-narration")
                        .contentType("application/json")
                        .content("""
                                {
                                  "duckingVolume": 0.125,
                                  "narrationVolume": 1.750,
                                  "fadeDurationMs": 400
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.125))
                .andExpect(jsonPath("$.mixSettings.narrationVolume").value(1.750))
                .andExpect(jsonPath("$.mixSettings.fadeDurationMs").value(400))
                .andExpect(jsonPath("$.segments[1].text").value("Explain the second scene."));

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace/mix-settings", "job-controller-job-narration")
                        .contentType("application/json")
                        .content("""
                                {
                                  "duckingVolume": 1.001,
                                  "narrationVolume": 1.000,
                                  "fadeDurationMs": 250
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-narration")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Invalid override.",
                                      "duckingVolume": 1.001
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/jobs/{jobId}/narration-workspace", "job-controller-job-narration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EMPTY"))
                .andExpect(jsonPath("$.segmentCount").value(0))
                .andExpect(jsonPath("$.generationReady").value(false))
                .andExpect(jsonPath("$.mixAutomation.keyframeCount").value(0))
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.125));
    }

    @Test
    void generatesNarrationAudioForLocalizationJobWithoutReplacingDeliveryArtifacts() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:12:00Z");
        createJob("job-controller-video-narration-audio", "job-controller-job-narration-audio", createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-narration-audio")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice"
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/jobs/{jobId}/narration-workspace/generate-audio", "job-controller-job-narration-audio"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-narration-audio"))
                .andExpect(jsonPath("$.filename").value("narration-audio.mp3"))
                .andExpect(jsonPath("$.contentType").value("audio/mpeg"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.totalCharacterCount").value(49))
                .andExpect(jsonPath("$.totalTimelineDurationSeconds").value(28.500))
                .andExpect(jsonPath("$.voiceSummary").value("PRESET:demo-voice"))
                .andExpect(jsonPath("$.audioLayout").value("TIMED_AUDIO_BED"))
                .andExpect(jsonPath("$.timeAligned").value(true))
                .andExpect(jsonPath("$.ttsCallCount").value(2))
                .andExpect(jsonPath("$.status").value("READY"));

        List<JobArtifactRecord> artifacts = artifactRepository.findByJobId("job-controller-job-narration-audio");
        assertThat(artifacts)
                .extracting(JobArtifactRecord::type)
                .containsExactly(JobArtifactType.NARRATION_AUDIO)
                .doesNotContain(
                        JobArtifactType.DUBBING_AUDIO,
                        JobArtifactType.DUBBED_VIDEO,
                        JobArtifactType.BURNED_VIDEO,
                        JobArtifactType.REVIEWED_BURNED_VIDEO
                );
    }

    @Test
    void previewsNarrationSegmentTtsWithoutCreatingArtifacts() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:12:30Z");
        createJob("video-narration-preview", "job-controller-job-narration-preview", createdAt);

        byte[] audio = mockMvc.perform(post(
                        "/api/jobs/{jobId}/narration-workspace/segment-preview",
                        "job-controller-job-narration-preview"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "text": "  Preview this line before saving.  ",
                                  "voice": "demo-voice"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"narration-segment-preview.mp3\""
                ))
                .andExpect(content().contentType("audio/mpeg"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(audio).isNotEmpty();
        assertThat(artifactRepository.findByJobId("job-controller-job-narration-preview")).isEmpty();
    }

    @Test
    void rejectsInvalidNarrationSegmentPreviewWithoutCreatingArtifacts() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:12:45Z");
        createJob("video-narration-preview-invalid", "job-narration-preview-invalid", createdAt);

        mockMvc.perform(post(
                        "/api/jobs/{jobId}/narration-workspace/segment-preview",
                        "job-narration-preview-invalid"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "text": "Preview this line.",
                                  "voice": "unknown"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(artifactRepository.findByJobId("job-narration-preview-invalid")).isEmpty();
    }

    @Test
    void generatesNarratedVideoForLocalizationJobWithoutReplacingDeliveryArtifacts() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:14:00Z");
        createJob("job-controller-video-narrated", "job-controller-job-narrated", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-narrated-base",
                "job-controller-job-narrated",
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/job-controller-job-narrated/job-controller-narrated-base/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                3L,
                "burned-video-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-narrated-audio",
                "job-controller-job-narrated",
                JobArtifactType.NARRATION_AUDIO,
                "job-artifacts/job-controller-job-narrated/job-controller-narrated-audio/narration-audio.mp3",
                "narration-audio.mp3",
                "audio/mpeg",
                3L,
                "narration-audio-hash",
                false,
                null,
                createdAt.plusSeconds(2)
        ));
        when(objectStorageService.open("job-artifacts/job-controller-job-narrated/job-controller-narrated-base/burned-video.mp4"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
        when(objectStorageService.open("job-artifacts/job-controller-job-narrated/job-controller-narrated-audio/narration-audio.mp3"))
                .thenReturn(new ByteArrayInputStream(new byte[] {4, 5, 6}));

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace/mix-settings", "job-controller-job-narrated")
                        .contentType("application/json")
                        .content("""
                                {
                                  "duckingVolume": 0.125,
                                  "narrationVolume": 1.750,
                                  "fadeDurationMs": 400
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.125))
                .andExpect(jsonPath("$.mixSettings.narrationVolume").value(1.750))
                .andExpect(jsonPath("$.mixSettings.fadeDurationMs").value(400));

        mockMvc.perform(post("/api/jobs/{jobId}/narration-workspace/generate-video", "job-controller-job-narrated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-narrated"))
                .andExpect(jsonPath("$.filename").value("narrated-video.mp4"))
                .andExpect(jsonPath("$.contentType").value("video/mp4"))
                .andExpect(jsonPath("$.sizeBytes").value(3))
                .andExpect(jsonPath("$.baseVideoType").value("BURNED_VIDEO"))
                .andExpect(jsonPath("$.narrationAudioArtifactId").value("job-controller-narrated-audio"))
                .andExpect(jsonPath("$.mixMode").value("DUCKED_ORIGINAL_AUDIO"))
                .andExpect(jsonPath("$.duckingVolume").value(0.125))
                .andExpect(jsonPath("$.narrationVolume").value(1.750))
                .andExpect(jsonPath("$.fadeDurationMs").value(400))
                .andExpect(jsonPath("$.narrationWindowCount").value(0))
                .andExpect(jsonPath("$.status").value("READY"));

        List<JobArtifactRecord> artifacts = artifactRepository.findByJobId("job-controller-job-narrated");
        assertThat(artifacts)
                .extracting(JobArtifactRecord::type)
                .contains(JobArtifactType.BURNED_VIDEO, JobArtifactType.NARRATION_AUDIO, JobArtifactType.NARRATED_VIDEO)
                .doesNotContain(
                        JobArtifactType.DUBBING_AUDIO,
                        JobArtifactType.DUBBED_VIDEO,
                        JobArtifactType.REVIEWED_BURNED_VIDEO
                );
    }

    @Test
    void returnsNarrationRenderReviewJsonAndMarkdown() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:15:00Z");
        createJob("job-controller-video-render-review", "job-controller-job-render-review", createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-render-review")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice"
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ],
                                  "mixKeyframes": [
                                    {
                                      "lane": "DUCKING_VOLUME",
                                      "timeSeconds": 15.000,
                                      "value": 0.350
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        artifactRepository.save(new JobArtifactRecord(
                "job-controller-render-review-audio",
                "job-controller-job-render-review",
                JobArtifactType.NARRATION_AUDIO,
                "job-artifacts/job-controller-job-render-review/job-controller-render-review-audio/narration-audio.mp3",
                "narration-audio.mp3",
                "audio/mpeg",
                3L,
                "narration-audio-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-render-review-video",
                "job-controller-job-render-review",
                JobArtifactType.NARRATED_VIDEO,
                "job-artifacts/job-controller-job-render-review/job-controller-render-review-video/narrated-video.mp4",
                "narrated-video.mp4",
                "video/mp4",
                3L,
                "narrated-video-hash",
                false,
                null,
                createdAt.plusSeconds(2)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-review-waveform",
                "job-controller-job-render-review",
                JobArtifactType.NARRATION_WAVEFORM,
                "job-artifacts/job-controller-job-render-review/job-controller-review-waveform/narration-waveform.json",
                "narration-waveform.json",
                "application/json",
                3L,
                "narration-waveform-hash",
                false,
                null,
                createdAt.plusSeconds(3)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}/narration-render-review", "job-controller-job-render-review"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-render-review"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.nextAction").value("Review narrated video and export handoff evidence."))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.audioReady").value(true))
                .andExpect(jsonPath("$.videoReady").value(true))
                .andExpect(jsonPath("$.waveformReady").value(true))
                .andExpect(jsonPath("$.waveformArtifactId").value("job-controller-review-waveform"))
                .andExpect(jsonPath("$.mixKeyframeCount").value(1))
                .andExpect(jsonPath("$.safeLinks[1].href").value("/api/jobs/job-controller-job-render-review/narration-render-review/markdown/download"));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/narration-render-review/markdown/download",
                        "job-controller-job-render-review"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"linguaframe-job-job-controller-job-render-review-narration-render-review.md\""
                ))
                .andExpect(content().contentType("text/markdown"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown)
                .contains("# Narration Render Review")
                .contains("- Status: READY")
                .contains("- Narration audio ready: true")
                .contains("- Narrated video ready: true")
                .doesNotContain("Explain the first scene.")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");
    }

    @Test
    void savesAndReturnsNarrationPlaybackReviewJsonAndMarkdown() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:18:00Z");
        createJob("job-controller-video-playback-review", "job-controller-job-playback-review", createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-playback-review")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice"
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        artifactRepository.save(new JobArtifactRecord(
                "playback-review-audio",
                "job-controller-job-playback-review",
                JobArtifactType.NARRATION_AUDIO,
                "job-artifacts/job-controller-job-playback-review/playback-review-audio/narration-audio.mp3",
                "narration-audio.mp3",
                "audio/mpeg",
                3L,
                "playback-audio-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "playback-review-video",
                "job-controller-job-playback-review",
                JobArtifactType.NARRATED_VIDEO,
                "job-artifacts/job-controller-job-playback-review/playback-review-video/narrated-video.mp4",
                "narrated-video.mp4",
                "video/mp4",
                3L,
                "playback-video-hash",
                false,
                null,
                createdAt.plusSeconds(2)
        ));

        mockMvc.perform(put(
                        "/api/jobs/{jobId}/narration-playback-review/segments/{segmentIndex}",
                        "job-controller-job-playback-review",
                        0
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision": "NEEDS_EDIT",
                                  "issueCategories": ["TEXT", "VOICE"],
                                  "reviewerNote": "Do not leak this playback reviewer note."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-playback-review"))
                .andExpect(jsonPath("$.status").value("ATTENTION"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.reviewedSegmentCount").value(1))
                .andExpect(jsonPath("$.needsEditCount").value(1))
                .andExpect(jsonPath("$.unreviewedSegmentCount").value(1))
                .andExpect(jsonPath("$.audioReady").value(true))
                .andExpect(jsonPath("$.videoReady").value(true))
                .andExpect(jsonPath("$.segments[0].decision").value("NEEDS_EDIT"))
                .andExpect(jsonPath("$.segments[0].issueCategories[0]").value("TEXT"))
                .andExpect(jsonPath("$.segments[0].reviewerNotePresent").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Do not leak this playback reviewer note."))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Explain the first scene."))));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/narration-playback-review/markdown/download",
                        "job-controller-job-playback-review"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"linguaframe-job-job-controller-job-playback-review-narration-playback-review.md\""
                ))
                .andExpect(content().contentType("text/markdown"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown)
                .contains("# Narration Playback Review")
                .contains("- Status: ATTENTION")
                .contains("- Needs edit: 1")
                .contains("Segment 0")
                .doesNotContain("Do not leak this playback reviewer note.")
                .doesNotContain("Explain the first scene.")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");
    }

    @Test
    void returnsNarrationPlaybackResolutionJsonAndMarkdown() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:19:00Z");
        createJob("video-playback-resolution", "job-playback-resolution", createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-playback-resolution")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice"
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        artifactRepository.save(new JobArtifactRecord(
                "playback-resolution-audio",
                "job-playback-resolution",
                JobArtifactType.NARRATION_AUDIO,
                "job-artifacts/job-playback-resolution/playback-resolution-audio/narration-audio.mp3",
                "narration-audio.mp3",
                "audio/mpeg",
                3L,
                "playback-resolution-audio-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(put(
                        "/api/jobs/{jobId}/narration-playback-review/segments/{segmentIndex}",
                        "job-playback-resolution",
                        0
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision": "NEEDS_RERENDER",
                                  "issueCategories": ["MIX", "VIDEO"],
                                  "reviewerNote": "Do not leak this playback resolution note."
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put(
                        "/api/jobs/{jobId}/narration-playback-review/segments/{segmentIndex}",
                        "job-playback-resolution",
                        1
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "decision": "ACCEPTED",
                                  "issueCategories": [],
                                  "reviewerNote": ""
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/narration-playback-review/resolution",
                        "job-playback-resolution"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-playback-resolution"))
                .andExpect(jsonPath("$.status").value("ATTENTION"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.readySegmentCount").value(1))
                .andExpect(jsonPath("$.unresolvedSegmentCount").value(1))
                .andExpect(jsonPath("$.rerenderRequiredCount").value(1))
                .andExpect(jsonPath("$.audioReady").value(true))
                .andExpect(jsonPath("$.videoReady").value(false))
                .andExpect(jsonPath("$.unresolvedSegments[0].segmentIndex").value(0))
                .andExpect(jsonPath("$.unresolvedSegments[0].resolutionStatus").value("RERENDER_REQUIRED"))
                .andExpect(jsonPath("$.unresolvedSegments[0].issueCategories[0]").value("MIX"))
                .andExpect(jsonPath("$.unresolvedSegments[0].reviewerNotePresent").value(true))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Do not leak this playback resolution note."))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Explain the first scene."))));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/narration-playback-review/resolution/markdown/download",
                        "job-playback-resolution"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"linguaframe-job-job-playback-resolution-narration-playback-resolution.md\""
                ))
                .andExpect(content().contentType("text/markdown"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown)
                .contains("# Narration Playback Resolution")
                .contains("- Status: ATTENTION")
                .contains("- Rerenders required: 1")
                .contains("Segment 0")
                .doesNotContain("Do not leak this playback resolution note.")
                .doesNotContain("Explain the first scene.")
                .doesNotContain("/Users/")
                .doesNotContain("sk-")
                .doesNotContain("objectKey");
    }

    @Test
    void returnsNarrationScriptPackageJsonMarkdownAndZip() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:16:00Z");
        createJob("job-controller-video-script-package", "job-controller-job-script-package", createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-script-package")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice"
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace/mix-settings", "job-controller-job-script-package")
                        .contentType("application/json")
                        .content("""
                                {
                                  "duckingVolume": 0.125,
                                  "narrationVolume": 1.750,
                                  "fadeDurationMs": 400
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/jobs/{jobId}/narration-script-package", "job-controller-job-script-package"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-script-package"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.totalCharacterCount").value(49))
                .andExpect(jsonPath("$.timelineGapCount").value(1))
                .andExpect(jsonPath("$.timelineGapSeconds").value(27.000))
                .andExpect(jsonPath("$.voiceSummary").value("PRESET:demo-voice"))
                .andExpect(jsonPath("$.defaultVoice").value("demo-voice"))
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.125))
                .andExpect(jsonPath("$.mixSettings.narrationVolume").value(1.750))
                .andExpect(jsonPath("$.mixSettings.fadeDurationMs").value(400))
                .andExpect(jsonPath("$.segments[0].text").value("Explain the first scene."))
                .andExpect(jsonPath("$.segments[0].voice").value("demo-voice"))
                .andExpect(jsonPath("$.safeLinks[0].href").value("/api/jobs/job-controller-job-script-package/narration-script-package"))
                .andExpect(jsonPath("$.packageEntries[0]").value("manifest.json"));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/narration-script-package/markdown/download",
                        "job-controller-job-script-package"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"linguaframe-job-job-controller-job-script-package-narration-script-package.md\""
                ))
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown)
                .contains("# Narration Script Package")
                .contains("Explain the first scene.")
                .contains("Explain the second scene.")
                .contains("- Voice summary: PRESET:demo-voice")
                .doesNotContain("source-videos/")
                .doesNotContain("provider request payload")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");

        byte[] zip = mockMvc.perform(get(
                        "/api/jobs/{jobId}/narration-script-package/download",
                        "job-controller-job-script-package"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"linguaframe-job-job-controller-job-script-package-narration-script-package.zip\""
                ))
                .andExpect(content().contentType("application/zip"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(zip);
        assertThat(entries)
                .containsKeys("manifest.json", "narration-script-package.json", "narration-script-package.md", "README.md");
        assertThat(String.join("\n", entries.values()))
                .contains("\"includesNarrationTextBodies\":true")
                .contains("Explain the first scene.")
                .doesNotContain("source-videos/")
                .doesNotContain("provider request payload")
                .doesNotContain("/Users/")
                .doesNotContain("sk-");
    }

    @Test
    void returnsDecodedNarrationWaveformJson() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:16:30Z");
        createJobWithDuration("job-controller-video-waveform", "job-controller-job-waveform", 120, createdAt);
        saveSmokeProofArtifact("waveform-narration", "job-controller-job-waveform", JobArtifactType.NARRATION_AUDIO);

        mockMvc.perform(get("/api/jobs/{jobId}/narration-waveform", "job-controller-job-waveform")
                        .param("bucketCount", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-waveform"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.sourceType").value("NARRATION_AUDIO"))
                .andExpect(jsonPath("$.bucketCount").value(96))
                .andExpect(jsonPath("$.durationSeconds").value(120.000))
                .andExpect(jsonPath("$.buckets[0].peak").value(0.750))
                .andExpect(jsonPath("$.buckets[0].rms").value(0.500))
                .andExpect(jsonPath("$.fallbackReason").value(""));
    }

    @Test
    void importsNarrationScriptPackageForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:17:00Z");
        createJobWithDuration("job-controller-video-script-import", "job-controller-job-script-import", 90, createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-script-import")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 1.000,
                                      "endSeconds": 2.000,
                                      "text": "Old script.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/jobs/{jobId}/narration-script-package/import", "job-controller-job-script-import")
                        .contentType("application/json")
                        .content("""
                                {
                                  "replaceExisting": true,
                                  "mixSettings": {
                                    "duckingVolume": 0.125,
                                    "narrationVolume": 1.750,
                                    "fadeDurationMs": 400
                                  },
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 28.000,
                                      "text": "Explain the first scene.",
                                      "voice": "demo-voice"
                                    },
                                    {
                                      "index": 1,
                                      "startSeconds": 55.000,
                                      "endSeconds": 70.500,
                                      "text": "Explain the second scene.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-script-import"))
                .andExpect(jsonPath("$.importedSegmentCount").value(2))
                .andExpect(jsonPath("$.totalCharacterCount").value(49))
                .andExpect(jsonPath("$.voiceSummary").value("PRESET:demo-voice"))
                .andExpect(jsonPath("$.replacedExisting").value(true))
                .andExpect(jsonPath("$.workspace.segments[0].text").value("Explain the first scene."))
                .andExpect(jsonPath("$.workspace.segments[1].text").value("Explain the second scene."))
                .andExpect(jsonPath("$.workspace.mixSettings.duckingVolume").value(0.125))
                .andExpect(jsonPath("$.workspace.mixSettings.narrationVolume").value(1.750))
                .andExpect(jsonPath("$.workspace.mixSettings.fadeDurationMs").value(400));

        mockMvc.perform(post("/api/jobs/{jobId}/narration-script-package/import", "job-controller-job-script-import")
                        .contentType("application/json")
                        .content("""
                                {
                                  "replaceExisting": true,
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 15.000,
                                      "endSeconds": 95.000,
                                      "text": "Invalid imported script.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/jobs/{jobId}/narration-workspace", "job-controller-job-script-import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.segments[0].text").value("Explain the first scene."))
                .andExpect(jsonPath("$.mixSettings.duckingVolume").value(0.125));
    }

    @Test
    void appliesNarrationDemoPresetToLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:17:30Z");
        createJobWithDuration("job-controller-video-demo-preset", "job-controller-job-demo-preset", 300, createdAt);

        mockMvc.perform(put("/api/jobs/{jobId}/narration-workspace", "job-controller-job-demo-preset")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "startSeconds": 1.000,
                                      "endSeconds": 2.000,
                                      "text": "Old script.",
                                      "voice": "demo-voice"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/jobs/{jobId}/narration-demo-preset/apply", "job-controller-job-demo-preset")
                        .contentType("application/json")
                        .content("""
                                {
                                  "presetId": "tears-showcase-narration",
                                  "replaceExisting": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-demo-preset"))
                .andExpect(jsonPath("$.presetId").value("tears-showcase-narration"))
                .andExpect(jsonPath("$.profileId").value("tears-showcase"))
                .andExpect(jsonPath("$.importedSegmentCount").value(4))
                .andExpect(jsonPath("$.voiceSummary").value("DEFAULT:demo-voice"))
                .andExpect(jsonPath("$.generatedMedia").value(false))
                .andExpect(jsonPath("$.workspace.segmentCount").value(4))
                .andExpect(jsonPath("$.workspace.segments[0].voice").doesNotExist())
                .andExpect(jsonPath("$.scriptPackage.status").value("READY"))
                .andExpect(jsonPath("$.narrationEvidenceStatus").value("ATTENTION"));

        mockMvc.perform(post("/api/jobs/{jobId}/narration-demo-preset/apply", "job-controller-job-demo-preset")
                        .contentType("application/json")
                        .content("""
                                {
                                  "presetId": "tears-showcase-narration",
                                  "replaceExisting": false
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/jobs/{jobId}/narration-workspace", "job-controller-job-demo-preset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentCount").value(4))
                .andExpect(jsonPath("$.segments[0].voice").doesNotExist());
    }

    @Test
    void preflightsNarrationDemoRenderBeforeGeneratingMedia() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:17:40Z");
        createJobWithDuration("job-controller-video-demo-preflight", "job-controller-job-demo-preflight", 300, createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-demo-preflight-base",
                "job-controller-job-demo-preflight",
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/job-controller-job-demo-preflight/job-controller-demo-preflight-base/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                3L,
                "burned-video-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(post("/api/jobs/{jobId}/narration-demo/render/preflight", "job-controller-job-demo-preflight")
                        .contentType("application/json")
                        .content("""
                                {
                                  "presetId": "tears-showcase-narration",
                                  "replaceExisting": true,
                                  "generateNarratedVideo": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-demo-preflight"))
                .andExpect(jsonPath("$.presetId").value("tears-showcase-narration"))
                .andExpect(jsonPath("$.status").value("ATTENTION"))
                .andExpect(jsonPath("$.providerMode").value("demo"))
                .andExpect(jsonPath("$.paidProvider").value(false))
                .andExpect(jsonPath("$.estimatedSegmentCount").value(4))
                .andExpect(jsonPath("$.estimatedCharacterCount").isNumber())
                .andExpect(jsonPath("$.existingWorkspaceSegmentCount").value(0))
                .andExpect(jsonPath("$.generateNarratedVideo").value(true))
                .andExpect(jsonPath("$.safeNextCommand").value("LINGUAFRAME_DEMO_JOB_ID=job-controller-job-demo-preflight scripts/demo/narration-demo-render.sh"))
                .andExpect(jsonPath("$.checks[0].key").value("PRESET"))
                .andExpect(jsonPath("$.checks[0].status").value("PASS"))
                .andExpect(jsonPath("$.checks[4].key").value("SCRIPT_PACKAGE"))
                .andExpect(jsonPath("$.checks[4].status").value("WARN"))
                .andExpect(jsonPath("$.evidenceRoutes[0]").value("/api/jobs/job-controller-job-demo-preflight/narration-demo/render"));
    }

    @Test
    void rendersNarrationDemoPresetAudioAndVideoForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:17:45Z");
        createJobWithDuration("job-controller-video-demo-render", "job-controller-job-demo-render", 300, createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-demo-render-base",
                "job-controller-job-demo-render",
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/job-controller-job-demo-render/job-controller-demo-render-base/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                3L,
                "burned-video-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        when(objectStorageService.open("job-artifacts/job-controller-job-demo-render/job-controller-demo-render-base/burned-video.mp4"))
                .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

        mockMvc.perform(post("/api/jobs/{jobId}/narration-demo/render", "job-controller-job-demo-render")
                        .contentType("application/json")
                        .content("""
                                {
                                  "presetId": "tears-showcase-narration",
                                  "replaceExisting": true,
                                  "generateNarratedVideo": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-demo-render"))
                .andExpect(jsonPath("$.presetId").value("tears-showcase-narration"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.steps[0].key").value("PRESET_APPLY"))
                .andExpect(jsonPath("$.steps[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[1].key").value("NARRATION_AUDIO"))
                .andExpect(jsonPath("$.steps[1].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.steps[2].key").value("NARRATED_VIDEO"))
                .andExpect(jsonPath("$.steps[2].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.presetApply.importedSegmentCount").value(4))
                .andExpect(jsonPath("$.narrationAudio.filename").value("narration-audio.mp3"))
                .andExpect(jsonPath("$.narratedVideo.filename").value("narrated-video.mp4"))
                .andExpect(jsonPath("$.scriptPackage.status").value("READY"))
                .andExpect(jsonPath("$.narrationEvidence.status").value("READY"))
                .andExpect(jsonPath("$.narrationEvidence.narrationAudioReady").value(true))
                .andExpect(jsonPath("$.narrationEvidence.narratedVideoReady").value(true))
                .andExpect(jsonPath("$.generatedArtifactCount").value(2));

        List<JobArtifactRecord> artifacts = artifactRepository.findByJobId("job-controller-job-demo-render");
        assertThat(artifacts)
                .extracting(JobArtifactRecord::type)
                .contains(JobArtifactType.BURNED_VIDEO, JobArtifactType.NARRATION_AUDIO, JobArtifactType.NARRATED_VIDEO);
    }

    @Test
    void rejectsNarrationDemoRenderWithoutReplaceConfirmation() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:17:50Z");
        createJobWithDuration("job-controller-video-render-bad", "job-controller-job-render-bad", 300, createdAt);

        mockMvc.perform(post("/api/jobs/{jobId}/narration-demo/render", "job-controller-job-render-bad")
                        .contentType("application/json")
                        .content("""
                                {
                                  "presetId": "tears-showcase-narration",
                                  "replaceExisting": false,
                                  "generateNarratedVideo": true
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsSubtitleReviewEvidenceJsonMarkdownAndPackage() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:18:00Z");
        createJob("job-controller-video-review-evidence", "job-controller-job-review-evidence", "review-evidence.mp4", LocalizationJobStatus.COMPLETED, createdAt);
        transcriptService.replaceTranscript("job-controller-job-review-evidence", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_000L, "First source line"),
                new TranscriptionSegmentBo(1, 1_200L, 2_800L, "Second source line")
        )));
        subtitleService.replaceSubtitles("job-controller-job-review-evidence", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行"),
                new TranslationSegmentBo(1, 1_200L, 2_800L, "第二行")
        )));
        mockMvc.perform(put("/api/jobs/{jobId}/subtitle-draft", "job-controller-job-review-evidence")
                        .param("language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {
                                      "index": 0,
                                      "text": "第一行",
                                      "decision": "ACCEPTED",
                                      "issueCategories": [],
                                      "reviewerNote": "accepted note must stay private"
                                    },
                                    {
                                      "index": 1,
                                      "text": "修正后的第二行",
                                      "decision": "EDITED",
                                      "issueCategories": ["TERM"],
                                      "reviewerNote": "edited note must stay private"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/jobs/{jobId}/subtitle-draft/publish", "job-controller-job-review-evidence")
                        .contentType("application/json")
                        .content("""
                                {
                                  "language": "zh-CN",
                                  "includeBurnedVideo": false
                                }
                                """))
                .andExpect(status().isOk());

        String evidenceJson = mockMvc.perform(get("/api/jobs/{jobId}/subtitle-review-evidence", "job-controller-job-review-evidence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-review-evidence"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.reviewedSegmentCount").value(2))
                .andExpect(jsonPath("$.acceptedSegmentCount").value(1))
                .andExpect(jsonPath("$.editedDecisionCount").value(1))
                .andExpect(jsonPath("$.annotationCount").value(1))
                .andExpect(jsonPath("$.reviewerNoteCount").value(2))
                .andExpect(jsonPath("$.reviewedSubtitleArtifactCount").value(3))
                .andExpect(jsonPath("$.issueCategoryCounts[0].category").value("TERM"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(evidenceJson)
                .doesNotContain("First source line")
                .doesNotContain("修正后的第二行")
                .doesNotContain("accepted note must stay private")
                .doesNotContain("edited note must stay private")
                .doesNotContain("job-artifacts/job-controller-job-review-evidence");

        String markdown = mockMvc.perform(get("/api/jobs/{jobId}/subtitle-review-evidence/markdown/download", "job-controller-job-review-evidence"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-job-review-evidence-subtitle-review-evidence.md\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(markdown)
                .contains("# Subtitle Review Evidence")
                .contains("- Status: READY")
                .contains("- TERM: 1")
                .doesNotContain("Second source line")
                .doesNotContain("edited note must stay private");

        byte[] packageBytes = mockMvc.perform(get("/api/jobs/{jobId}/subtitle-review-evidence/download", "job-controller-job-review-evidence"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-job-review-evidence-subtitle-review-evidence.zip\"")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        Map<String, String> entries = readZipEntries(packageBytes);
        assertThat(entries.keySet()).containsExactlyInAnyOrder(
                "manifest.json",
                "subtitle-review-evidence.md",
                "review-summary.json",
                "release-notes.md",
                "README.md"
        );
        assertThat(entries.get("manifest.json")).contains("\"includesReviewerNoteBodies\":false");
        assertThat(entries.get("subtitle-review-evidence.md"))
                .doesNotContain("第一行")
                .doesNotContain("edited note must stay private");
    }

    @Test
    void rejectsSubtitleDraftUpdateForUnknownSegmentIndex() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:15:00Z");
        createJob("job-controller-video-draft-invalid", "job-controller-job-draft-invalid", createdAt);
        subtitleService.replaceSubtitles("job-controller-job-draft-invalid", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行")
        )));

        mockMvc.perform(put("/api/jobs/{jobId}/subtitle-draft", "job-controller-job-draft-invalid")
                        .param("language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {"index": 9, "text": "无效行"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Subtitle draft segment index does not exist: 9"));
    }

    @Test
    void publishesReviewedSubtitleArtifactsForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:20:00Z");
        createJob("job-controller-video-reviewed", "job-controller-job-reviewed", createdAt);
        subtitleService.replaceSubtitles("job-controller-job-reviewed", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行"),
                new TranslationSegmentBo(1, 1_200L, 2_800L, "第二行")
        )));
        mockMvc.perform(put("/api/jobs/{jobId}/subtitle-draft", "job-controller-job-reviewed")
                        .param("language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {"index": 1, "text": "修正后的第二行"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/jobs/{jobId}/subtitle-draft/publish", "job-controller-job-reviewed")
                        .contentType("application/json")
                        .content("""
                                {
                                  "language": "zh-CN",
                                  "includeBurnedVideo": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-reviewed"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.burnedVideoRequested").value(false))
                .andExpect(jsonPath("$.burnedVideoCreated").value(false))
                .andExpect(jsonPath("$.artifacts.length()").value(3))
                .andExpect(jsonPath("$.artifacts[0].type").value("REVIEWED_SUBTITLE_JSON"))
                .andExpect(jsonPath("$.artifacts[0].filename").value("reviewed-subtitles.zh-CN.json"))
                .andExpect(jsonPath("$.artifacts[1].type").value("REVIEWED_SUBTITLE_SRT"))
                .andExpect(jsonPath("$.artifacts[2].type").value("REVIEWED_SUBTITLE_VTT"));

        mockMvc.perform(get("/api/jobs/{jobId}/artifacts", "job-controller-job-reviewed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("REVIEWED_SUBTITLE_JSON"))
                .andExpect(jsonPath("$[1].type").value("REVIEWED_SUBTITLE_SRT"))
                .andExpect(jsonPath("$[2].type").value("REVIEWED_SUBTITLE_VTT"));

        List<JobArtifactRecord> reviewedArtifacts = artifactRepository.findByJobId("job-controller-job-reviewed");
        JobArtifactRecord reviewedSrt = reviewedArtifacts.stream()
                .filter(artifact -> artifact.type() == JobArtifactType.REVIEWED_SUBTITLE_SRT)
                .findFirst()
                .orElseThrow();
        when(objectStorageService.open(reviewedSrt.objectKey()))
                .thenReturn(new ByteArrayInputStream("reviewed srt 修正后的第二行".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "job-controller-job-reviewed",
                        reviewedSrt.id()
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"reviewed-subtitles.zh-CN.srt\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("修正后的第二行")));

        for (JobArtifactRecord artifact : reviewedArtifacts) {
            when(objectStorageService.open(artifact.objectKey()))
                    .thenReturn(new ByteArrayInputStream(("archive " + artifact.type()).getBytes(StandardCharsets.UTF_8)));
        }
        byte[] archive = mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/archive/download",
                        "job-controller-job-reviewed"
                ))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        Map<String, String> entries = readZipEntries(archive);
        assertThat(entries.keySet()).anySatisfy(name ->
                assertThat(name).contains("REVIEWED_SUBTITLE_SRT").contains("reviewed-subtitles.zh-CN.srt"));
        assertThat(entries.get("manifest.json")).contains("REVIEWED_SUBTITLE_JSON");
    }

    @Test
    void returnsReviewedSubtitleWorkflowCockpitForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:22:00Z");
        createJob("job-controller-video-workflow", "job-controller-job-workflow", "workflow.mp4", LocalizationJobStatus.COMPLETED, createdAt);
        transcriptService.replaceTranscript("job-controller-job-workflow", new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_000L, "First source line"),
                new TranscriptionSegmentBo(1, 1_200L, 2_800L, "Second source line")
        )));
        subtitleService.replaceSubtitles("job-controller-job-workflow", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行")
        )));
        artifactRepository.save(new JobArtifactRecord(
                "workflow-target-json",
                "job-controller-job-workflow",
                JobArtifactType.TARGET_SUBTITLE_JSON,
                "job-artifacts/job-controller-job-workflow/target-subtitles.json",
                "target-subtitles.zh-CN.json",
                "application/json",
                10L,
                "workflow-json-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "workflow-target-srt",
                "job-controller-job-workflow",
                JobArtifactType.TARGET_SUBTITLE_SRT,
                "job-artifacts/job-controller-job-workflow/target-subtitles.srt",
                "target-subtitles.zh-CN.srt",
                "application/x-subrip",
                10L,
                "workflow-srt-hash",
                false,
                null,
                createdAt.plusSeconds(2)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "workflow-target-vtt",
                "job-controller-job-workflow",
                JobArtifactType.TARGET_SUBTITLE_VTT,
                "job-artifacts/job-controller-job-workflow/target-subtitles.vtt",
                "target-subtitles.zh-CN.vtt",
                "text/vtt",
                10L,
                "workflow-vtt-hash",
                false,
                null,
                createdAt.plusSeconds(3)
        ));

        String reviewNeededJson = mockMvc.perform(get("/api/jobs/{jobId}/reviewed-subtitle-workflow", "job-controller-job-workflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-workflow"))
                .andExpect(jsonPath("$.overallStatus").value("ATTENTION"))
                .andExpect(jsonPath("$.phase").value("REVIEW_NEEDED"))
                .andExpect(jsonPath("$.segmentCount").value(2))
                .andExpect(jsonPath("$.missingTargetCount").value(1))
                .andExpect(jsonPath("$.generatedSubtitleArtifactCount").value(3))
                .andExpect(jsonPath("$.links[?(@.kind == 'SUBTITLE_REVIEW')]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(reviewNeededJson)
                .doesNotContain("First source line")
                .doesNotContain("第一行")
                .doesNotContain("job-artifacts/job-controller-job-workflow");

        subtitleService.replaceSubtitles("job-controller-job-workflow", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(0, 0L, 1_000L, "第一行"),
                new TranslationSegmentBo(1, 1_200L, 2_800L, "第二行")
        )));
        mockMvc.perform(put("/api/jobs/{jobId}/subtitle-draft", "job-controller-job-workflow")
                        .param("language", "zh-CN")
                        .contentType("application/json")
                        .content("""
                                {
                                  "segments": [
                                    {"index": 1, "text": "补齐后的第二行"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/jobs/{jobId}/subtitle-draft/publish", "job-controller-job-workflow")
                        .contentType("application/json")
                        .content("""
                                {
                                  "language": "zh-CN",
                                  "includeBurnedVideo": false
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/jobs/{jobId}/reviewed-subtitle-workflow", "job-controller-job-workflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallStatus").value("READY"))
                .andExpect(jsonPath("$.phase").value("HANDOFF_READY"))
                .andExpect(jsonPath("$.reviewedSubtitleArtifactCount").value(3))
                .andExpect(jsonPath("$.handoffReady").value(true))
                .andExpect(jsonPath("$.links[?(@.kind == 'HANDOFF_PACKAGE')]").exists());
    }

    @Test
    void returnsDeliveryManifestJsonAndMarkdownForLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T01:25:00Z");
        createJob("job-controller-video-manifest", "job-controller-job-manifest", "manifest.mp4", LocalizationJobStatus.COMPLETED, createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "manifest-reviewed-json",
                "job-controller-job-manifest",
                JobArtifactType.REVIEWED_SUBTITLE_JSON,
                "job-artifacts/job-controller-job-manifest/reviewed-json/reviewed-subtitles.zh-CN.json",
                "reviewed-subtitles.zh-CN.json",
                "application/json",
                128L,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                false,
                null,
                createdAt.plusSeconds(1)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "manifest-reviewed-srt",
                "job-controller-job-manifest",
                JobArtifactType.REVIEWED_SUBTITLE_SRT,
                "job-artifacts/job-controller-job-manifest/reviewed-srt/reviewed-subtitles.zh-CN.srt",
                "reviewed-subtitles.zh-CN.srt",
                "application/x-subrip",
                64L,
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                false,
                null,
                createdAt.plusSeconds(2)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "manifest-reviewed-vtt",
                "job-controller-job-manifest",
                JobArtifactType.REVIEWED_SUBTITLE_VTT,
                "job-artifacts/job-controller-job-manifest/reviewed-vtt/reviewed-subtitles.zh-CN.vtt",
                "reviewed-subtitles.zh-CN.vtt",
                "text/vtt",
                72L,
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
                false,
                null,
                createdAt.plusSeconds(3)
        ));
        artifactRepository.save(new JobArtifactRecord(
                "manifest-worker",
                "job-controller-job-manifest",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-manifest/worker/worker-summary.json",
                "worker-summary.json",
                "application/json",
                42L,
                "1111111111111111111111111111111111111111111111111111111111111111",
                false,
                null,
                createdAt.plusSeconds(4)
        ));

        mockMvc.perform(get("/api/jobs/{jobId}/delivery-manifest", "job-controller-job-manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-job-manifest"))
                .andExpect(jsonPath("$.handoffReady").value(true))
                .andExpect(jsonPath("$.reviewedSubtitleArtifactCount").value(3))
                .andExpect(jsonPath("$.reviewedArtifacts[1].downloadUrl").value(
                        "/api/jobs/job-controller-job-manifest/artifacts/manifest-reviewed-srt/download"
                ))
                .andExpect(jsonPath("$.auditArtifacts[0].filename").value("worker-summary.json"))
                .andExpect(jsonPath("$.links[0].url").value("/api/jobs/job-controller-job-manifest/artifacts/archive/download"));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/delivery-manifest/markdown/download",
                        "job-controller-job-manifest"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-job-manifest-delivery-manifest.md\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown).contains("reviewed-subtitles.zh-CN.srt");
        assertThat(markdown).contains("/api/jobs/job-controller-job-manifest/evidence/bundle/download");
        assertThat(markdown).doesNotContain("job-artifacts/job-controller-job-manifest");
        assertThat(markdown).doesNotContain("raw transcript");
        assertThat(markdown).doesNotContain("raw corrected subtitle");
        assertThat(markdown).doesNotContain("OPENAI_API_KEY");
    }

    @Test
    void comparesTwoLocalizationJobsAsSafeDemoEvidence() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T15:30:00Z");
        createJob("job-controller-video-comparison", "job-controller-comparison-baseline", "comparison.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-comparison-showcase",
                "job-controller-video-comparison",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        updateComparisonSettings(
                "job-controller-comparison-baseline",
                "quick-baseline",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF"
        );
        updateComparisonSettings(
                "job-controller-comparison-showcase",
                "tears-showcase",
                "FORMAL",
                "HIGH_CONTRAST",
                3,
                "abc123",
                "BALANCED"
        );
        modelCallAuditService.recordSuccess(modelCall("job-controller-comparison-baseline", 100, 100, 80, "0.00006300"));
        modelCallAuditService.recordSuccess(modelCall("job-controller-comparison-showcase", 100, 100, 80, "0.00006300"));
        modelCallAuditService.recordSuccess(modelCall("job-controller-comparison-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-comparison-baseline", "job-controller-comparison-baseline", 82, createdAt.plusSeconds(20)));
        qualityEvaluationRepository.save(quality("quality-comparison-showcase", "job-controller-comparison-showcase", 91, createdAt.plusSeconds(21)));
        artifactRepository.save(reviewedArtifact("comparison-baseline-json", "job-controller-comparison-baseline", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("comparison-baseline-srt", "job-controller-comparison-baseline", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("comparison-baseline-vtt", "job-controller-comparison-baseline", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/comparison/{comparisonJobId}",
                        "job-controller-comparison-baseline",
                        "job-controller-comparison-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseline.jobId").value("job-controller-comparison-baseline"))
                .andExpect(jsonPath("$.comparison.jobId").value("job-controller-comparison-showcase"))
                .andExpect(jsonPath("$.sameVideo").value(true))
                .andExpect(jsonPath("$.baseline.demoProfileId").value("quick-baseline"))
                .andExpect(jsonPath("$.comparison.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.baseline.handoffReady").value(true))
                .andExpect(jsonPath("$.comparison.handoffReady").value(false))
                .andExpect(jsonPath("$.delta.qualityScore").value(9))
                .andExpect(jsonPath("$.delta.modelCallCount").value(1))
                .andExpect(jsonPath("$.settingDiffs[0].field").value("demoProfileId"))
                .andExpect(jsonPath("$.settingDiffs[1].field").value("translationStyle"));

        String markdown = mockMvc.perform(get(
                        "/api/jobs/{jobId}/comparison/{comparisonJobId}/markdown/download",
                        "job-controller-comparison-baseline",
                        "job-controller-comparison-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        startsWith("attachment; filename=\"linguaframe-job-job-controller-comparison-baseline-vs-job-controller-comparison-showcase-comparison.md\"")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(markdown).contains("- Baseline demo profile: quick-baseline");
        assertThat(markdown).contains("- Comparison demo profile: tears-showcase");
        assertThat(markdown).contains("- Quality score delta: +9");
        assertThat(markdown).doesNotContain("source-videos/");
        assertThat(markdown).doesNotContain("provider payload");
        assertThat(markdown).doesNotContain("OPENAI_API_KEY");
    }

    @Test
    void returnsDemoRunMatrixForSameSourceProfileRuns() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T16:30:00Z");
        createJob("job-controller-video-matrix", "job-controller-matrix-baseline", "matrix.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-matrix-showcase",
                "job-controller-video-matrix",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-matrix-failed",
                "job-controller-video-matrix",
                "zh-CN",
                LocalizationJobStatus.FAILED,
                createdAt.plusSeconds(20)
        ));
        updateComparisonSettings("job-controller-matrix-baseline", "quick-baseline", "NATURAL", "STANDARD", 0, "", "OFF");
        updateComparisonSettings("job-controller-matrix-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        updateComparisonSettings("job-controller-matrix-failed", "concise-review", "CONCISE", "LARGE", 0, "", "STRICT");
        modelCallAuditService.recordSuccess(modelCall("job-controller-matrix-baseline", 100, 100, 80, "0.00006300"));
        modelCallAuditService.recordSuccess(modelCall("job-controller-matrix-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-matrix-baseline", "job-controller-matrix-baseline", 82, createdAt.plusSeconds(30)));
        qualityEvaluationRepository.save(quality("quality-matrix-showcase", "job-controller-matrix-showcase", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("matrix-baseline-json", "job-controller-matrix-baseline", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("matrix-baseline-srt", "job-controller-matrix-baseline", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("matrix-baseline-vtt", "job-controller-matrix-baseline", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-run-matrix",
                        "job-controller-matrix-showcase"
                ).param("limit", "8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorJobId").value("job-controller-matrix-showcase"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-matrix"))
                .andExpect(jsonPath("$.recommendedBaselineJobId").value("job-controller-matrix-baseline"))
                .andExpect(jsonPath("$.bestQualityJobId").value("job-controller-matrix-showcase"))
                .andExpect(jsonPath("$.lowestCostJobId").value("job-controller-matrix-baseline"))
                .andExpect(jsonPath("$.jobs[0].jobId").value("job-controller-matrix-failed"))
                .andExpect(jsonPath("$.jobs[0].demoProfileId").value("concise-review"))
                .andExpect(jsonPath("$.jobs[1].jobId").value("job-controller-matrix-showcase"))
                .andExpect(jsonPath("$.jobs[1].qualityScore").value(91))
                .andExpect(jsonPath("$.jobs[1].handoffReady").value(false))
                .andExpect(jsonPath("$.jobs[2].jobId").value("job-controller-matrix-baseline"))
                .andExpect(jsonPath("$.jobs[2].handoffReady").value(true));
    }

    @Test
    void returnsDemoPresenterPackForSelectedCompletedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T17:30:00Z");
        createJob("job-controller-video-presenter", "job-controller-presenter-baseline", "presenter.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-presenter-showcase",
                "job-controller-video-presenter",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        updateComparisonSettings("job-controller-presenter-baseline", "quick-baseline", "NATURAL", "STANDARD", 0, "", "OFF");
        updateComparisonSettings("job-controller-presenter-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-presenter-baseline", 100, 100, 80, "0.00006300"));
        modelCallAuditService.recordSuccess(modelCall("job-controller-presenter-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-presenter-baseline", "job-controller-presenter-baseline", 82, createdAt.plusSeconds(30)));
        qualityEvaluationRepository.save(quality("quality-presenter-showcase", "job-controller-presenter-showcase", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("presenter-showcase-json", "job-controller-presenter-showcase", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("presenter-showcase-srt", "job-controller-presenter-showcase", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("presenter-showcase-vtt", "job-controller-presenter-showcase", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-presenter-pack",
                        "job-controller-presenter-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.anchorJobId").value("job-controller-presenter-showcase"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-presenter"))
                .andExpect(jsonPath("$.readinessStatus").value("READY"))
                .andExpect(jsonPath("$.recommendedBaselineJobId").value("job-controller-presenter-baseline"))
                .andExpect(jsonPath("$.bestQualityJobId").value("job-controller-presenter-showcase"))
                .andExpect(jsonPath("$.runs[0].jobId").value("job-controller-presenter-showcase"))
                .andExpect(jsonPath("$.runs[0].roles[0]").value("ANCHOR"))
                .andExpect(jsonPath("$.runs[0].roles[1]").value("BEST_QUALITY"))
                .andExpect(jsonPath("$.runs[1].jobId").value("job-controller-presenter-baseline"))
                .andExpect(jsonPath("$.runs[1].roles[0]").value("RECOMMENDED_BASELINE"))
                .andExpect(jsonPath("$.downloads[?(@.kind == 'DEMO_RUN_PACKAGE')].url")
                        .value("/api/jobs/job-controller-presenter-showcase/demo-run-package/download"))
                .andExpect(jsonPath("$.downloads[?(@.kind == 'AI_AUDIT_PACKAGE')].url")
                        .value("/api/jobs/job-controller-presenter-showcase/ai-audit-package/download"))
                .andExpect(jsonPath("$.downloads[?(@.kind == 'SOURCE_MEDIA')].url")
                        .value("/api/media/uploads/job-controller-video-presenter/source/download"))
                .andExpect(jsonPath("$.presenterNotesMarkdown").value(org.hamcrest.Matchers.containsString(
                        "LinguaFrame Demo Presenter Pack"
                )))
                .andExpect(jsonPath("$.presenterNotesMarkdown").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("provider payload")
                )));
    }

    @Test
    void returnsDemoReplayCardForSelectedCompletedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T10:30:00Z");
        createJob("job-controller-video-replay", "job-controller-replay-baseline", "replay.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-replay-showcase",
                "job-controller-video-replay",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        updateComparisonSettings("job-controller-replay-baseline", "quick-baseline", "NATURAL", "STANDARD", 0, "", "OFF");
        updateComparisonSettings("job-controller-replay-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-replay-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-replay-showcase", "job-controller-replay-showcase", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("replay-showcase-json", "job-controller-replay-showcase", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("replay-showcase-srt", "job-controller-replay-showcase", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("replay-showcase-vtt", "job-controller-replay-showcase", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-replay-card",
                        "job-controller-replay-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-replay-showcase"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-replay"))
                .andExpect(jsonPath("$.readiness").value("READY"))
                .andExpect(jsonPath("$.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.recommendedBaselineJobId").value("job-controller-replay-baseline"))
                .andExpect(jsonPath("$.settings[?(@.key == 'translationGlossary')].value").value("3 entries / abc123"))
                .andExpect(jsonPath("$.commands[?(@.kind == 'EXPORT_REPLAY_CARD')].command")
                        .value("LINGUAFRAME_DEMO_JOB_ID=job-controller-replay-showcase scripts/demo/demo-replay-card.sh"))
                .andExpect(jsonPath("$.commands[?(@.kind == 'COMPARE_WITH_BASELINE')].command").value(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString(
                                "LINGUAFRAME_COMPARISON_BASELINE_JOB_ID=job-controller-replay-baseline"
                        ))
                ))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_RUN_PACKAGE')].url")
                        .value("/api/jobs/job-controller-replay-showcase/demo-run-package/download"))
                .andExpect(jsonPath("$.safetyNotes").value(org.hamcrest.Matchers.hasItem(
                        "Local source paths are intentionally omitted; choose the source file again before replaying."
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void returnsDemoCompletionCertificateForSelectedCompletedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T11:45:00Z");
        createJob("job-controller-video-certificate", "job-controller-certificate-baseline", "certificate.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-certificate-showcase",
                "job-controller-video-certificate",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        updateComparisonSettings("job-controller-certificate-baseline", "quick-baseline", "NATURAL", "STANDARD", 0, "", "OFF");
        updateComparisonSettings("job-controller-certificate-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-certificate-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-certificate-showcase", "job-controller-certificate-showcase", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("certificate-showcase-json", "job-controller-certificate-showcase", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("certificate-showcase-srt", "job-controller-certificate-showcase", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("certificate-showcase-vtt", "job-controller-certificate-showcase", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-completion-certificate",
                        "job-controller-certificate-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-certificate-showcase"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-certificate"))
                .andExpect(jsonPath("$.certificateStatus").value("READY"))
                .andExpect(jsonPath("$.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.recommendedBaselineJobId").value("job-controller-certificate-baseline"))
                .andExpect(jsonPath("$.checks[?(@.key == 'JOB_COMPLETED')].status").value("PASS"))
                .andExpect(jsonPath("$.checks[?(@.key == 'HANDOFF_READY')].status").value("PASS"))
                .andExpect(jsonPath("$.sections[?(@.key == 'REPRODUCIBILITY')].facts").value(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("demo-replay-card.sh")))
                ))
                .andExpect(jsonPath("$.links[?(@.kind == 'CERTIFICATE_JSON')].url")
                        .value("/api/jobs/job-controller-certificate-showcase/demo-completion-certificate"))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_RUN_PACKAGE')].url")
                        .value("/api/jobs/job-controller-certificate-showcase/demo-run-package/download"))
                .andExpect(jsonPath("$.safetyNotes").value(org.hamcrest.Matchers.hasItem(
                        "The certificate is generated on demand from existing safe evidence routes and does not create new artifacts."
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))));
    }

    @Test
    void returnsDemoAcceptanceGateForSelectedCompletedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T11:50:00Z");
        createJob("job-controller-video-acceptance", "job-controller-acceptance-baseline", "acceptance.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-acceptance-showcase",
                "job-controller-video-acceptance",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        updateComparisonSettings("job-controller-acceptance-baseline", "quick-baseline", "NATURAL", "STANDARD", 0, "", "OFF");
        updateComparisonSettings("job-controller-acceptance-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-acceptance-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-acceptance-showcase", "job-controller-acceptance-showcase", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("acceptance-showcase-json", "job-controller-acceptance-showcase", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("acceptance-showcase-srt", "job-controller-acceptance-showcase", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("acceptance-showcase-vtt", "job-controller-acceptance-showcase", JobArtifactType.REVIEWED_SUBTITLE_VTT));
        artifactRepository.save(reviewedArtifact("acceptance-showcase-dubbed", "job-controller-acceptance-showcase", JobArtifactType.DUBBED_VIDEO));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-acceptance-gate",
                        "job-controller-acceptance-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-acceptance-showcase"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-acceptance"))
                .andExpect(jsonPath("$.gateStatus").value("READY"))
                .andExpect(jsonPath("$.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.checks[?(@.key == 'JOB_COMPLETED')].status").value("PASS"))
                .andExpect(jsonPath("$.checks[?(@.key == 'MEDIA_OUTPUT_AVAILABLE')].status").value("PASS"))
                .andExpect(jsonPath("$.checks[?(@.key == 'COMPLETION_CERTIFICATE_READY')].status").value("PASS"))
                .andExpect(jsonPath("$.checks[?(@.key == 'NARRATION_PLAYBACK_RESOLVED')].status").value("PASS"))
                .andExpect(jsonPath("$.evidence[?(@.key == 'MEDIA_OUTPUT_COUNT')].value").value("1"))
                .andExpect(jsonPath("$.evidence[?(@.key == 'NARRATION_PLAYBACK_RESOLUTION_STATUS')].value").value("BLOCKED"))
                .andExpect(jsonPath("$.evidence[?(@.key == 'NARRATION_PLAYBACK_RESOLUTION_STATUS')].status").value("READY"))
                .andExpect(jsonPath("$.links[?(@.kind == 'ACCEPTANCE_GATE_JSON')].url")
                        .value("/api/jobs/job-controller-acceptance-showcase/demo-acceptance-gate"))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_RUN_PACKAGE')].url")
                        .value("/api/jobs/job-controller-acceptance-showcase/demo-run-package/download"))
                .andExpect(jsonPath("$.safetyNotes").value(org.hamcrest.Matchers.hasItem(
                        "The gate is generated on demand from existing safe evidence surfaces and does not create artifacts or call providers."
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))));
    }

    @Test
    void returnsDemoRunVarianceReport() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T12:05:00Z");
        createJob("job-controller-video-variance", "job-controller-variance", "variance.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        updateComparisonSettings("job-controller-variance", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-variance", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-variance", "job-controller-variance", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("variance-json", "job-controller-variance", JobArtifactType.REVIEWED_SUBTITLE_JSON));

        mockMvc.perform(post(
                        "/api/jobs/{jobId}/demo-run-variance",
                        "job-controller-variance"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "preUploadJson": "{\\"overallStatus\\":\\"READY\\",\\"estimatedCostUsd\\":\\"0.01000000\\",\\"estimatedDurationSecondsUpper\\":120,\\"sourceReuseDecision\\":{\\"status\\":\\"UPLOAD_NEW_SOURCE\\"},\\"stages\\":[{\\"executionType\\":\\"PAID\\"}]}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-variance"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-variance"))
                .andExpect(jsonPath("$.overallStatus").value("READY"))
                .andExpect(jsonPath("$.baselineMode").value("EXECUTION_PLAN"))
                .andExpect(jsonPath("$.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.metrics[?(@.id == 'estimatedCostUsd')].status").value("LOWER_THAN_ESTIMATE"))
                .andExpect(jsonPath("$.metrics[?(@.id == 'modelCallCount')].status").value("MATCH"))
                .andExpect(jsonPath("$.metrics[?(@.id == 'sourceReuseDecision')].estimatedValue").value("UPLOAD_NEW_SOURCE"))
                .andExpect(jsonPath("$.safeLinks").value(org.hamcrest.Matchers.hasItem(
                        "/api/jobs/job-controller-variance/demo-run-package/download"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("source-videos/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoRunVarianceMarkdown() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T12:10:00Z");
        createJob("job-controller-video-variance-md", "job-controller-variance-md", "variance-md.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        modelCallAuditService.recordSuccess(modelCall("job-controller-variance-md", 130, 120, 100, "0.00007800"));

        mockMvc.perform(post(
                        "/api/jobs/{jobId}/demo-run-variance/markdown/download",
                        "job-controller-variance-md"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "preUploadJson": "{\\"overallStatus\\":\\"READY\\",\\"estimatedCostUsd\\":\\"0.01000000\\",\\"estimatedDurationSecondsUpper\\":120,\\"stages\\":[{\\"executionType\\":\\"PAID\\"}]}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo-run-variance.md\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# Demo Run Variance Report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("## Variance Metrics")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/api/jobs/job-controller-variance-md/demo-run-package/download")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("source-videos/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void returnsActualOnlyDemoRunVarianceWithoutBaseline() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T12:15:00Z");
        createJob("job-controller-video-variance-actual", "job-controller-variance-actual", "variance-actual.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);

        mockMvc.perform(post(
                        "/api/jobs/{jobId}/demo-run-variance",
                        "job-controller-variance-actual"
                )
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-variance-actual"))
                .andExpect(jsonPath("$.baselineMode").value("MISSING"))
                .andExpect(jsonPath("$.notes").value(org.hamcrest.Matchers.hasItem(
                        "No pre-upload baseline was supplied; report is actual-only."
                )))
                .andExpect(jsonPath("$.metrics[?(@.id == 'estimatedCostUsd')].status").value("BASELINE_MISSING"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("source-videos/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void returnsDemoEvidenceClosurePackage() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T12:20:00Z");
        createJob("job-controller-video-closure", "job-controller-closure", "closure.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        updateComparisonSettings("job-controller-closure", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-closure", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-closure", "job-controller-closure", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("closure-json", "job-controller-closure", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("closure-srt", "job-controller-closure", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("closure-vtt", "job-controller-closure", JobArtifactType.REVIEWED_SUBTITLE_VTT));
        artifactRepository.save(reviewedArtifact("closure-dubbed", "job-controller-closure", JobArtifactType.DUBBED_VIDEO));

        mockMvc.perform(post(
                        "/api/jobs/{jobId}/demo-evidence-closure",
                        "job-controller-closure"
                )
                        .contentType("application/json")
                        .content("""
                                {
                                  "preUploadJson": "{\\"overallStatus\\":\\"READY\\",\\"estimatedCostUsd\\":\\"0.01000000\\",\\"estimatedDurationSecondsUpper\\":120,\\"stages\\":[{\\"executionType\\":\\"PAID\\"}]}"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-closure"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-closure"))
                .andExpect(jsonPath("$.closureStatus").value("READY"))
                .andExpect(jsonPath("$.baselineMode").value("EXECUTION_PLAN"))
                .andExpect(jsonPath("$.varianceReport.overallStatus").value("READY"))
                .andExpect(jsonPath("$.sections[?(@.key == 'ACCEPTANCE_GATE')].status").value("READY"))
                .andExpect(jsonPath("$.sections[?(@.key == 'COMPLETION_CERTIFICATE')].status").value("READY"))
                .andExpect(jsonPath("$.safeLinks").value(org.hamcrest.Matchers.hasItem(
                        "/api/jobs/job-controller-closure/demo-evidence-closure/download"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("source-videos/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw provider payload"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoEvidenceClosureMarkdown() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T12:25:00Z");
        createJob("job-controller-video-closure-md", "job-controller-closure-md", "closure-md.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        modelCallAuditService.recordSuccess(modelCall("job-controller-closure-md", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-closure-md", "job-controller-closure-md", 91, createdAt.plusSeconds(31)));

        mockMvc.perform(post(
                        "/api/jobs/{jobId}/demo-evidence-closure/markdown/download",
                        "job-controller-closure-md"
                )
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"demo-evidence-closure.md\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# Demo Evidence Closure Package")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("## Post-Run Variance")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Baseline mode: `MISSING`")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("source-videos/"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoEvidenceClosureZip() throws Exception {
        Instant createdAt = Instant.parse("2026-06-29T12:30:00Z");
        createJob("job-controller-video-closure-zip", "job-controller-closure-zip", "closure-zip.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        modelCallAuditService.recordSuccess(modelCall("job-controller-closure-zip", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-closure-zip", "job-controller-closure-zip", 91, createdAt.plusSeconds(31)));

        byte[] body = mockMvc.perform(post(
                        "/api/jobs/{jobId}/demo-evidence-closure/download",
                        "job-controller-closure-zip"
                )
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"linguaframe-job-job-controller-closure-zip-demo-evidence-closure.zip\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(body))) {
            java.util.ArrayList<String> entries = new java.util.ArrayList<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
            assertThat(entries).containsExactlyInAnyOrder(
                    "manifest.json",
                    "demo-evidence-closure.md",
                    "demo-run-variance.md",
                    "README.md"
            );
        }
    }

    @Test
    void returnsDemoShareSheetForSelectedCompletedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T17:45:00Z");
        createJob("job-controller-video-share", "job-controller-share-baseline", "share.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        jobRepository.save(new LocalizationJobRecord(
                "job-controller-share-showcase",
                "job-controller-video-share",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt.plusSeconds(10)
        ));
        updateComparisonSettings("job-controller-share-baseline", "quick-baseline", "NATURAL", "STANDARD", 0, "", "OFF");
        updateComparisonSettings("job-controller-share-showcase", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-share-showcase", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-share-showcase", "job-controller-share-showcase", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("share-showcase-json", "job-controller-share-showcase", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("share-showcase-srt", "job-controller-share-showcase", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("share-showcase-vtt", "job-controller-share-showcase", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-share-sheet",
                        "job-controller-share-showcase"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-share-showcase"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-share"))
                .andExpect(jsonPath("$.readiness").value("READY"))
                .andExpect(jsonPath("$.headline").value("tears-showcase demo to zh-CN"))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("COMPLETED")))
                .andExpect(jsonPath("$.outcomeBullets").value(org.hamcrest.Matchers.hasItem("Quality score: 91 (GOOD)")))
                .andExpect(jsonPath("$.recommendedNextAction").value("Open the demo run package or reviewed handoff package for reviewer delivery."))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_RUN_PACKAGE')].url")
                        .value("/api/jobs/job-controller-share-showcase/demo-run-package/download"))
                .andExpect(jsonPath("$.links[?(@.kind == 'HANDOFF_PACKAGE')].url")
                        .value("/api/jobs/job-controller-share-showcase/handoff-package/download"))
                .andExpect(jsonPath("$.links[?(@.kind == 'SOURCE_MEDIA')].url")
                        .value("/api/media/uploads/job-controller-video-share/source/download"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString(
                        "# tears-showcase demo to zh-CN"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void downloadsDemoShareSheetMarkdownForSelectedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T18:00:00Z");
        createJob("job-controller-video-share-md", "job-controller-share-md", "share-md.mp4",
                LocalizationJobStatus.FAILED, createdAt);

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-share-sheet/markdown/download",
                        "job-controller-share-md"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-share-md-demo-share-sheet.md\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# manual demo to zh-CN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("- Readiness: NEEDS_ATTENTION")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Review diagnostics and failure triage before sharing this run.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void returnsDemoRunMonitorForSelectedProcessingJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T18:15:00Z");
        createJob("job-controller-video-monitor", "job-controller-monitor", "monitor.mp4",
                LocalizationJobStatus.QUEUED, createdAt);
        jobRepository.claimForExecution("job-controller-monitor", createdAt.plusSeconds(30));
        dispatchEventRepository.save(new JobDispatchEventRecord(
                "job-controller-monitor-dispatch",
                "job-controller-monitor",
                JobDispatchEventType.LOCALIZATION_JOB_QUEUED,
                "{}",
                JobDispatchEventStatus.DISPATCHED,
                1,
                createdAt.plusSeconds(20),
                null,
                createdAt.plusSeconds(21),
                createdAt.plusSeconds(20),
                createdAt.plusSeconds(21)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-monitor-worker",
                "job-controller-monitor",
                LocalizationJobStage.WORKER_RECEIVED,
                JobTimelineEventStatus.SUCCEEDED,
                "worker received",
                1000L,
                null,
                createdAt.plusSeconds(31)
        ));
        timelineEventRepository.save(new JobTimelineEventRecord(
                "job-controller-monitor-translation",
                "job-controller-monitor",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.STARTED,
                "raw transcript text /Users/example sk-test provider payload",
                null,
                null,
                createdAt.plusSeconds(40)
        ));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-run-monitor",
                        "job-controller-monitor"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-monitor"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-monitor"))
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.dispatchStatus").value("DISPATCHED"))
                .andExpect(jsonPath("$.currentStage").value("TARGET_SUBTITLE_EXPORT"))
                .andExpect(jsonPath("$.completedStageCount").value(1))
                .andExpect(jsonPath("$.attentionLevel").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("RUNNING"),
                        org.hamcrest.Matchers.is("ATTENTION")
                )))
                .andExpect(jsonPath("$.links[?(@.kind == 'JOB_DETAIL')].url")
                        .value("/api/jobs/job-controller-monitor"))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_SHARE_SHEET')].url")
                        .value("/api/jobs/job-controller-monitor/demo-share-sheet"))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString(
                        "LinguaFrame Demo Run Monitor"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))));
    }

    @Test
    void downloadsDemoRunMonitorMarkdownForSelectedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T18:30:00Z");
        createJob("job-controller-video-monitor-md", "job-controller-monitor-md", "monitor-md.mp4",
                LocalizationJobStatus.FAILED, createdAt);
        jobRepository.claimForExecution("job-controller-monitor-md", createdAt.plusSeconds(1));
        jobRepository.markFailed(
                "job-controller-monitor-md",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "raw transcript text /Users/example sk-test provider payload",
                createdAt.plusSeconds(60)
        );

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-run-monitor/markdown/download",
                        "job-controller-monitor-md"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/markdown;charset=UTF-8"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-monitor-md-demo-run-monitor.md\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("# LinguaFrame Demo Run Monitor")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("- Attention level: BLOCKED")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Open diagnostics")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))));
    }

    @Test
    void returnsDemoRunSnapshotForSelectedCompletedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T18:45:00Z");
        createJob("job-controller-video-snapshot", "job-controller-snapshot", "snapshot.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        updateComparisonSettings("job-controller-snapshot", "tears-showcase", "FORMAL", "HIGH_CONTRAST", 3, "abc123", "BALANCED");
        modelCallAuditService.recordSuccess(modelCall("job-controller-snapshot", 130, 120, 100, "0.00007800"));
        qualityEvaluationRepository.save(quality("quality-snapshot", "job-controller-snapshot", 91, createdAt.plusSeconds(31)));
        artifactRepository.save(reviewedArtifact("snapshot-json", "job-controller-snapshot", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("snapshot-srt", "job-controller-snapshot", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("snapshot-vtt", "job-controller-snapshot", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-run-snapshot",
                        "job-controller-snapshot"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-controller-snapshot"))
                .andExpect(jsonPath("$.videoId").value("job-controller-video-snapshot"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.demoProfileId").value("tears-showcase"))
                .andExpect(jsonPath("$.readiness").value("READY"))
                .andExpect(jsonPath("$.headline").value("tears-showcase demo to zh-CN"))
                .andExpect(jsonPath("$.sections[?(@.kind == 'INDEX_HTML')].filename").value("index.html"))
                .andExpect(jsonPath("$.sections[?(@.kind == 'SHARE_SHEET')].filename").value("demo-share-sheet.md"))
                .andExpect(jsonPath("$.sections[?(@.kind == 'RUN_MONITOR')].filename").value("demo-run-monitor.md"))
                .andExpect(jsonPath("$.packageEntries").value(org.hamcrest.Matchers.hasItem("index.html")))
                .andExpect(jsonPath("$.packageEntries").value(org.hamcrest.Matchers.hasItem("demo-share-sheet.json")))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_RUN_SNAPSHOT_DOWNLOAD')].url")
                        .value("/api/jobs/job-controller-snapshot/demo-run-snapshot/download"))
                .andExpect(jsonPath("$.links[?(@.kind == 'DEMO_RUN_PACKAGE')].url")
                        .value("/api/jobs/job-controller-snapshot/demo-run-package/download"))
                .andExpect(jsonPath("$.exclusionPolicy").value(org.hamcrest.Matchers.hasItem("media bytes")))
                .andExpect(jsonPath("$.markdown").value(org.hamcrest.Matchers.containsString("LinguaFrame Demo Snapshot")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("raw transcript text"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/Users/example"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sk-test"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("provider payload"))));
    }

    @Test
    void downloadsDemoRunSnapshotZipForSelectedJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-27T19:00:00Z");
        createJob("job-controller-video-snapshot-zip", "job-controller-snapshot-zip", "snapshot-zip.mp4",
                LocalizationJobStatus.COMPLETED, createdAt);
        updateComparisonSettings("job-controller-snapshot-zip", "tears-showcase", "NATURAL", "STANDARD", 0, "", "OFF");
        artifactRepository.save(reviewedArtifact("snapshot-zip-json", "job-controller-snapshot-zip", JobArtifactType.REVIEWED_SUBTITLE_JSON));
        artifactRepository.save(reviewedArtifact("snapshot-zip-srt", "job-controller-snapshot-zip", JobArtifactType.REVIEWED_SUBTITLE_SRT));
        artifactRepository.save(reviewedArtifact("snapshot-zip-vtt", "job-controller-snapshot-zip", JobArtifactType.REVIEWED_SUBTITLE_VTT));

        byte[] body = mockMvc.perform(get(
                        "/api/jobs/{jobId}/demo-run-snapshot/download",
                        "job-controller-snapshot-zip"
                ))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename=\"linguaframe-job-job-controller-snapshot-zip-demo-run-snapshot.zip\"")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        Map<String, String> entries = readZipEntries(body);
        assertThat(entries)
                .containsKeys(
                        "index.html",
                        "manifest.json",
                        "README.md",
                        "demo-share-sheet.md",
                        "demo-share-sheet.json",
                        "demo-run-monitor.md",
                        "demo-run-monitor.json",
                        "presenter-pack.json",
                        "delivery-manifest.md",
                        "diagnostics.json",
                        "evidence.md"
                );
        assertThat(entries.get("index.html"))
                .contains("LinguaFrame Demo Snapshot")
                .contains("demo-share-sheet.md");
        String combined = String.join("\n", entries.values());
        assertThat(combined)
                .doesNotContain("raw transcript text")
                .doesNotContain("/Users/example")
                .doesNotContain("sk-test")
                .doesNotContain("provider payload")
                .doesNotContain("job-artifacts/");
    }

    @Test
    void returnsNotFoundWhenArtifactDoesNotBelongToJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T23:30:00Z");
        createJob("job-controller-video-artifact-owner", "job-controller-job-artifact-owner", createdAt);
        createJob("job-controller-video-artifact-other", "job-controller-job-artifact-other", createdAt);
        artifactRepository.save(new JobArtifactRecord(
                "job-controller-artifact-owner",
                "job-controller-job-artifact-owner",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/job-controller-job-artifact-owner/job-controller-artifact-owner/worker-summary.json",
                "worker-summary.json",
                "application/json",
                2L,
                "owner-hash",
                false,
                null,
                createdAt.plusSeconds(1)
        ));

        mockMvc.perform(get(
                        "/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "job-controller-job-artifact-other",
                        "job-controller-artifact-owner"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void rejectsRetryForNonFailedLocalizationJob() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T21:00:00Z");
        createJob("job-controller-video-not-failed", "job-controller-job-not-failed", createdAt);

        mockMvc.perform(post("/api/jobs/{jobId}/retry", "job-controller-job-not-failed"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    private static Map<String, String> readZipEntries(byte[] zipBytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    @Test
    void rejectsRetryWhenConfiguredRetryLimitIsReached() throws Exception {
        Instant createdAt = Instant.parse("2026-06-26T21:15:00Z");
        createJob("job-controller-video-retry-limit", "job-controller-job-retry-limit", createdAt);
        jobRepository.claimForExecution("job-controller-job-retry-limit", createdAt.plusSeconds(1));
        jobRepository.markFailed(
                "job-controller-job-retry-limit",
                LocalizationJobStage.WORKER_SMOKE,
                "second execution failed",
                createdAt.plusSeconds(2)
        );
        jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET retry_count = 1
                        WHERE id = :jobId
                        """)
                .param("jobId", "job-controller-job-retry-limit")
                .update();

        mockMvc.perform(post("/api/jobs/{jobId}/retry", "job-controller-job-retry-limit"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Retry limit reached for this localization job."));
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/jobs/{jobId}", "missing-job"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    private void createJob(String videoId, String jobId, Instant createdAt) {
        createJob(videoId, jobId, "sample.mp4", LocalizationJobStatus.QUEUED, createdAt);
    }

    private void createJobWithDuration(String videoId, String jobId, int durationSeconds, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample.mp4",
                "video/mp4",
                123L,
                durationSeconds,
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

    private void createJob(
            String videoId,
            String jobId,
            String filename,
            LocalizationJobStatus status,
            Instant createdAt
    ) {
        videoRepository.save(new VideoRecord(
                videoId,
                filename,
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

    private void createReviewerWorkspaceJob(String jobId, String videoId, Instant createdAt) {
        createJob(videoId, jobId, "reviewer.mp4", LocalizationJobStatus.COMPLETED, createdAt);
        updateComparisonSettings(jobId, "tears-showcase", "FORMAL", "HIGH_CONTRAST", 1, "hash-reviewer", "BALANCED");
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini-transcribe",
                "openai-audio-transcriptions-v1",
                250L,
                null,
                null,
                new BigDecimal("30.0"),
                null,
                "audioSeconds=30",
                "segments=4"
        ));
        modelCallAuditService.recordSuccess(new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4.1-mini",
                "openai-subtitle-translation-v1",
                550L,
                1000,
                500,
                null,
                null,
                "target=zh-CN, segments=4",
                "translatedSegmentCount=4"
        ));
        qualityEvaluationRepository.save(quality("quality-" + jobId, jobId, 91, createdAt.plusSeconds(20)));
        String suffix = Integer.toHexString(jobId.hashCode()).replace("-", "n");
        saveSmokeProofArtifact("rv-tj-" + suffix, jobId, JobArtifactType.TRANSCRIPT_JSON);
        saveSmokeProofArtifact("rv-j-" + suffix, jobId, JobArtifactType.TARGET_SUBTITLE_JSON);
        saveSmokeProofArtifact("rv-s-" + suffix, jobId, JobArtifactType.TARGET_SUBTITLE_SRT);
        saveSmokeProofArtifact("rv-v-" + suffix, jobId, JobArtifactType.TARGET_SUBTITLE_VTT);
        saveSmokeProofArtifact("rv-bv-" + suffix, jobId, JobArtifactType.BURNED_VIDEO);
        saveSmokeProofArtifact("rv-dv-" + suffix, jobId, JobArtifactType.DUBBED_VIDEO);
        saveSmokeProofArtifact("rv-da-" + suffix, jobId, JobArtifactType.DUBBING_AUDIO);
        saveSmokeProofArtifact("rv-rj-" + suffix, jobId, JobArtifactType.REVIEWED_SUBTITLE_JSON);
        saveSmokeProofArtifact("rv-rs-" + suffix, jobId, JobArtifactType.REVIEWED_SUBTITLE_SRT);
        saveSmokeProofArtifact("rv-rv-" + suffix, jobId, JobArtifactType.REVIEWED_SUBTITLE_VTT);
        saveSmokeProofArtifact("rv-rb-" + suffix, jobId, JobArtifactType.REVIEWED_BURNED_VIDEO);
    }

    private void updateComparisonSettings(
            String jobId,
            String profile,
            String translationStyle,
            String subtitleStylePreset,
            int glossaryCount,
            String glossaryHash,
            String polishingMode
    ) {
        jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET demo_profile_id = :profile,
                            translation_style = :translationStyle,
                            subtitle_style_preset = :subtitleStylePreset,
                            translation_glossary_entry_count = :glossaryCount,
                            translation_glossary_hash = :glossaryHash,
                            subtitle_polishing_mode = :polishingMode
                        WHERE id = :jobId
                        """)
                .param("profile", profile)
                .param("translationStyle", translationStyle)
                .param("subtitleStylePreset", subtitleStylePreset)
                .param("glossaryCount", glossaryCount)
                .param("glossaryHash", glossaryHash)
                .param("polishingMode", polishingMode)
                .param("jobId", jobId)
                .update();
    }

    private CreateModelCallRecordCommand modelCall(
            String jobId,
            long latencyMs,
            int inputTokens,
            int outputTokens,
            String estimatedCostUsd
    ) {
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-subtitle-translation-v1",
                latencyMs,
                inputTokens,
                outputTokens,
                new BigDecimal(estimatedCostUsd),
                null,
                "target=zh-CN, segments=2, sourceChars=61",
                "segments=2, targetChars=29"
        );
    }

    private QualityEvaluationRecord quality(String evaluationId, String jobId, int score, Instant createdAt) {
        return new QualityEvaluationRecord(
                evaluationId,
                jobId,
                "zh-CN",
                score,
                "GOOD",
                score,
                score,
                score,
                score,
                List.of(),
                List.of(),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt
        );
    }

    private JobArtifactRecord reviewedArtifact(String artifactId, String jobId, JobArtifactType type) {
        return new JobArtifactRecord(
                artifactId,
                jobId,
                type,
                "job-artifacts/" + jobId + "/" + artifactId + "/reviewed-subtitles.zh-CN.json",
                "reviewed-subtitles.zh-CN.json",
                "application/json",
                42L,
                artifactId + "-hash",
                false,
                null,
                Instant.parse("2026-06-27T15:40:00Z")
        );
    }

    private void saveSmokeProofArtifact(String artifactId, String jobId, JobArtifactType type) {
        artifactRepository.save(new JobArtifactRecord(
                artifactId,
                jobId,
                type,
                "job-artifacts/" + jobId + "/" + artifactId + "/" + smokeProofFilename(type),
                smokeProofFilename(type),
                smokeProofContentType(type),
                128L,
                artifactId + "-hash",
                false,
                null,
                Instant.parse("2026-06-27T15:45:00Z")
        ));
    }

    private String smokeProofFilename(JobArtifactType type) {
        return switch (type) {
            case TRANSCRIPT_JSON -> "transcript.json";
            case TARGET_SUBTITLE_JSON -> "target-subtitles.zh-CN.json";
            case TARGET_SUBTITLE_SRT -> "target-subtitles.zh-CN.srt";
            case TARGET_SUBTITLE_VTT -> "target-subtitles.zh-CN.vtt";
            default -> type.name().toLowerCase() + ".bin";
        };
    }

    private String smokeProofContentType(JobArtifactType type) {
        return switch (type) {
            case TRANSCRIPT_JSON, TARGET_SUBTITLE_JSON -> "application/json";
            case TARGET_SUBTITLE_SRT -> "application/x-subrip";
            case TARGET_SUBTITLE_VTT -> "text/vtt";
            default -> "application/octet-stream";
        };
    }
}
