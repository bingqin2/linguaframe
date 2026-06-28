package com.linguaframe.job.controller;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
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
import com.linguaframe.media.repository.VideoRepository;
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

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM quality_evaluations").update();
        jdbcClient.sql("DELETE FROM model_call_records").update();
        jdbcClient.sql("DELETE FROM subtitle_draft_segments").update();
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
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
                                    {"index": 1, "text": "修正后的第二行"}
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
                .andExpect(jsonPath("$.segments[1].edited").value(true));

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
                .andExpect(jsonPath("$.segments[1].edited").value(false));
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
}
