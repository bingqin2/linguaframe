package com.linguaframe.job.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.linguaframe.common.security.DemoOwnerIdentityService;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.FailureTriageCategory;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobDispatchEventRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.impl.FailureTriageServiceImpl;
import com.linguaframe.job.service.impl.JobPipelineProgressServiceImpl;
import com.linguaframe.job.service.impl.LocalizationJobQueryServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalizationJobQueryServiceTests {

    private final LocalizationJobRepository jobRepository = mock(LocalizationJobRepository.class);
    private final JobArtifactRepository artifactRepository = mock(JobArtifactRepository.class);
    private final JobDispatchEventRepository dispatchEventRepository = mock(JobDispatchEventRepository.class);
    private final JobTimelineEventRepository timelineEventRepository = mock(JobTimelineEventRepository.class);
    private final ModelCallAuditService modelCallAuditService = mock(ModelCallAuditService.class);
    private final QualityEvaluationService qualityEvaluationService = mock(QualityEvaluationService.class);
    private final LocalizationJobStatusCacheService cacheService = mock(LocalizationJobStatusCacheService.class);
    private final FailureTriageService failureTriageService = new FailureTriageServiceImpl();
    private final JobPipelineProgressService pipelineProgressService = new JobPipelineProgressServiceImpl();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void getJobReturnsCachedSnapshotAfterOwnerValidation() {
        LocalizationJobVo cachedJob = job("job-query-cache-hit", LocalizationJobStatus.PROCESSING);
        when(jobRepository.findByIdAndOwnerId("job-query-cache-hit", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-hit",
                "video-query-cache-hit",
                "zh-CN",
                LocalizationJobStatus.PROCESSING,
                Instant.parse("2026-06-27T05:30:00Z")
        )));
        when(cacheService.get("job-query-cache-hit")).thenReturn(Optional.of(cachedJob));
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-cache-hit");

        assertThat(result).isEqualTo(cachedJob);
        verify(jobRepository, never()).findById("job-query-cache-hit");
        verify(cacheService, never()).put(cachedJob);
    }

    @Test
    void listJobsUsesCurrentDemoOwnerScope() {
        LocalizationJobQueryServiceImpl service = service(new FixedDemoOwnerIdentityService("owner-alpha"));

        service.listJobs(LocalizationJobStatus.COMPLETED, 5, 0);

        verify(jobRepository).findSummariesByOwnerId("owner-alpha", LocalizationJobStatus.COMPLETED, 5, 0);
        verify(jobRepository).countSummariesByOwnerId("owner-alpha", LocalizationJobStatus.COMPLETED);
        verify(jobRepository, never()).findSummaries(LocalizationJobStatus.COMPLETED, 5, 0);
    }

    @Test
    void listJobsByVideoIdUsesCurrentDemoOwnerScope() {
        LocalizationJobQueryServiceImpl service = service(new FixedDemoOwnerIdentityService("owner-alpha"));

        service.listJobsByVideoId("video-owner-alpha", 5);

        verify(jobRepository).findSummariesByVideoIdAndOwnerId("video-owner-alpha", "owner-alpha", 5);
        verify(jobRepository).countSummariesByVideoIdAndOwnerId("video-owner-alpha", "owner-alpha");
        verify(jobRepository, never()).findSummariesByVideoId("video-owner-alpha", 5);
    }

    @Test
    void getJobValidatesOwnerBeforeReturningCachedSnapshot() {
        LocalizationJobVo cachedJob = job("job-query-cache-owner", LocalizationJobStatus.PROCESSING);
        when(cacheService.get("job-query-cache-owner")).thenReturn(Optional.of(cachedJob));
        when(jobRepository.findByIdAndOwnerId("job-query-cache-owner", "owner-alpha")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service(new FixedDemoOwnerIdentityService("owner-alpha"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getJob("job-query-cache-owner"))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Localization job not found");
    }

    @Test
    void getJobReadsDatabaseByOwnerWhenCacheMisses() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-owner-miss")).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndOwnerId("job-query-owner-miss", "owner-alpha")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-owner-miss",
                "video-query-owner-miss",
                "owner-alpha",
                "zh-CN",
                null,
                "NATURAL",
                "STANDARD",
                "[]",
                "",
                0,
                "OFF",
                null,
                LocalizationJobStatus.QUEUED,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-owner-miss")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-owner-miss")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-owner-miss")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-owner-miss")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-owner-miss")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-owner-miss")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service(new FixedDemoOwnerIdentityService("owner-alpha"));

        LocalizationJobVo result = service.getJob("job-query-owner-miss");

        assertThat(result.jobId()).isEqualTo("job-query-owner-miss");
        verify(jobRepository).findByIdAndOwnerId("job-query-owner-miss", "owner-alpha");
        verify(jobRepository, never()).findById("job-query-owner-miss");
    }

    @Test
    void getJobCachesSnapshotAfterDatabaseReadOnMiss() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-cache-miss")).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndOwnerId("job-query-cache-miss", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-miss",
                "video-query-cache-miss",
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-cache-miss")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-cache-miss")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-cache-miss")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-cache-miss")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-cache-miss")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-cache-miss")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-cache-miss");

        assertThat(result.jobId()).isEqualTo("job-query-cache-miss");
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.QUEUED);
        verify(cacheService).put(result);
    }

    @Test
    void cacheReadFailureFallsBackToDatabaseReadAndCachesFreshSnapshot() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-cache-error")).thenThrow(new IllegalStateException("cache unavailable"));
        when(jobRepository.findByIdAndOwnerId("job-query-cache-error", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-error",
                "video-query-cache-error",
                "zh-CN",
                LocalizationJobStatus.PROCESSING,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-cache-error")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-cache-error")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-cache-error")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-cache-error")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-cache-error")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-cache-error")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-cache-error");

        assertThat(result.jobId()).isEqualTo("job-query-cache-error");
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.PROCESSING);
        verify(cacheService).put(result);
    }

    @Test
    void cacheWriteFailureDoesNotBreakJobRead() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-cache-write-error")).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndOwnerId("job-query-cache-write-error", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-cache-write-error",
                "video-query-cache-write-error",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-cache-write-error")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-cache-write-error")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-cache-write-error")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-cache-write-error")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-cache-write-error")).thenReturn(List.of());
        when(qualityEvaluationService.latestForJob("job-query-cache-write-error")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();
        doThrow(new IllegalStateException("cache unavailable")).when(cacheService).put(org.mockito.Mockito.any());

        LocalizationJobVo result = service.getJob("job-query-cache-write-error");

        assertThat(result.jobId()).isEqualTo("job-query-cache-write-error");
        assertThat(result.status()).isEqualTo(LocalizationJobStatus.COMPLETED);
    }

    @Test
    void getDiagnosticsReportIncludesSanitizedJobDetailAndArtifactMetadata() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        Instant artifactCreatedAt = Instant.parse("2026-06-27T05:35:00Z");
        when(cacheService.get("job-diagnostics-complete")).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndOwnerId("job-diagnostics-complete", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-diagnostics-complete",
                "video-diagnostics-complete",
                "zh-CN",
                LocalizationJobStatus.COMPLETED,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-diagnostics-complete")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-diagnostics-complete")).thenReturn(List.of(new JobArtifactRecord(
                "artifact-diagnostics-burned-video",
                "job-diagnostics-complete",
                JobArtifactType.BURNED_VIDEO,
                "jobs/job-diagnostics-complete/private/burned-video.mp4",
                "localized-video.mp4",
                "video/mp4",
                2048,
                "sha256-diagnostics-burned-video",
                true,
                "source-artifact-diagnostics",
                artifactCreatedAt
        )));
        when(timelineEventRepository.findByJobId("job-diagnostics-complete")).thenReturn(List.of(new JobTimelineEventRecord(
                "timeline-diagnostics-complete",
                "job-diagnostics-complete",
                LocalizationJobStage.SUBTITLE_BURN_IN,
                JobTimelineEventStatus.SUCCEEDED,
                "Burned subtitles into localized video",
                1500L,
                null,
                Instant.parse("2026-06-27T05:34:00Z")
        )));
        when(modelCallAuditService.summarizeJob("job-diagnostics-complete")).thenReturn(new JobUsageSummaryVo(
                1,
                0,
                900L,
                new BigDecimal("0.01230000"),
                100,
                30,
                null,
                320
        ));
        when(modelCallAuditService.listModelCalls("job-diagnostics-complete")).thenReturn(List.of(new ModelCallVo(
                "model-call-diagnostics",
                "job-diagnostics-complete",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini",
                "translation-v1",
                ModelCallStatus.SUCCEEDED,
                900L,
                100,
                30,
                null,
                320,
                "Translated 320 characters",
                "Produced target-language subtitles",
                "demo-owner",
                new BigDecimal("0.01230000"),
                null,
                Instant.parse("2026-06-27T05:33:00Z")
        )));
        when(qualityEvaluationService.latestForJob("job-diagnostics-complete")).thenReturn(Optional.of(new QualityEvaluationVo(
                "quality-diagnostics",
                "job-diagnostics-complete",
                "zh-CN",
                91,
                "PASS",
                95,
                90,
                88,
                92,
                List.of("Minor timing drift"),
                List.of("Review subtitle line 4"),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                Instant.parse("2026-06-27T05:36:00Z")
        )));
        LocalizationJobQueryServiceImpl service = service();

        JobDiagnosticsReportVo result = service.getDiagnosticsReport("job-diagnostics-complete");

        assertThat(result.generatedAt()).isNotNull();
        assertThat(result.job().jobId()).isEqualTo("job-diagnostics-complete");
        assertThat(result.job().status()).isEqualTo(LocalizationJobStatus.COMPLETED);
        assertThat(result.job().timelineEvents()).hasSize(1);
        assertThat(result.job().pipelineProgress().currentStage()).isEqualTo(LocalizationJobStage.SUBTITLE_BURN_IN);
        assertThat(result.job().pipelineProgress().totalMeasuredDurationMs()).isEqualTo(1500L);
        assertThat(result.job().usageSummary().modelCallCount()).isEqualTo(1);
        assertThat(result.job().modelCalls()).hasSize(1);
        assertThat(result.job().qualityEvaluation().score()).isEqualTo(91);
        assertThat(result.artifactCount()).isEqualTo(1);
        assertThat(result.artifacts()).singleElement()
                .satisfies(artifact -> {
                    assertThat(artifact.artifactId()).isEqualTo("artifact-diagnostics-burned-video");
                    assertThat(artifact.type()).isEqualTo(JobArtifactType.BURNED_VIDEO);
                    assertThat(artifact.filename()).isEqualTo("localized-video.mp4");
                    assertThat(artifact.contentSha256()).isEqualTo("sha256-diagnostics-burned-video");
                    assertThat(artifact.cacheHit()).isTrue();
                    assertThat(artifact.sourceArtifactId()).isEqualTo("source-artifact-diagnostics");
                    assertThat(artifact.createdAt()).isEqualTo(artifactCreatedAt);
                });
    }

    @Test
    void diagnosticsReportSerializationExcludesPrivateStorageAndRawPayloadData() throws JsonProcessingException {
        when(jobRepository.findByIdAndOwnerId("job-diagnostics-safe", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-diagnostics-safe",
                "video-diagnostics-safe",
                "zh-CN",
                LocalizationJobStatus.FAILED,
                Instant.parse("2026-06-27T05:30:00Z")
        )));
        when(cacheService.get("job-diagnostics-safe")).thenReturn(Optional.of(job(
                "job-diagnostics-safe",
                LocalizationJobStatus.FAILED
        )));
        when(artifactRepository.findByJobId("job-diagnostics-safe")).thenReturn(List.of(new JobArtifactRecord(
                "artifact-diagnostics-safe",
                "job-diagnostics-safe",
                JobArtifactType.TRANSCRIPT_JSON,
                "/Users/wangbingqin/private/raw-transcript-object-key.json",
                "transcript.json",
                "application/json",
                512,
                "sha256-diagnostics-safe",
                false,
                null,
                Instant.parse("2026-06-27T05:35:00Z")
        )));
        LocalizationJobQueryServiceImpl service = service();

        String json = objectMapper.writeValueAsString(service.getDiagnosticsReport("job-diagnostics-safe"));

        assertThat(json).contains("job-diagnostics-safe");
        assertThat(json).contains("transcript.json");
        assertThat(json).contains("sha256-diagnostics-safe");
        assertThat(json).doesNotContain("/Users/wangbingqin");
        assertThat(json).doesNotContain("private/raw-transcript-object-key.json");
        assertThat(json).doesNotContain("raw transcript text");
        assertThat(json).doesNotContain("raw subtitle text");
        assertThat(json).doesNotContain("\"prompt\"");
        assertThat(json).doesNotContain("provider request payload");
    }

    @Test
    void getJobIncludesFailureTriageForFailedJobs() {
        Instant createdAt = Instant.parse("2026-06-27T05:30:00Z");
        when(cacheService.get("job-query-triage")).thenReturn(Optional.empty());
        when(jobRepository.findByIdAndOwnerId("job-query-triage", "demo-owner")).thenReturn(Optional.of(new LocalizationJobRecord(
                "job-query-triage",
                "video-query-triage",
                "zh-CN",
                LocalizationJobStatus.FAILED,
                createdAt,
                createdAt,
                null,
                createdAt,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                "OpenAI request failed with status 401 invalid_api_key",
                0,
                createdAt
        )));
        when(dispatchEventRepository.findLatestByJobId("job-query-triage")).thenReturn(Optional.empty());
        when(artifactRepository.findByJobId("job-query-triage")).thenReturn(List.of());
        when(timelineEventRepository.findByJobId("job-query-triage")).thenReturn(List.of());
        when(modelCallAuditService.summarizeJob("job-query-triage")).thenReturn(emptyUsage());
        when(modelCallAuditService.listModelCalls("job-query-triage")).thenReturn(List.of(new ModelCallVo(
                "model-call-query-triage",
                "job-query-triage",
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.OPENAI,
                "gpt-4o-mini",
                "translation-v1",
                ModelCallStatus.FAILED,
                900L,
                100,
                null,
                null,
                300,
                "target=zh-CN, segments=2",
                null,
                "demo-owner",
                BigDecimal.ZERO,
                "OpenAI request failed with status 401 invalid_api_key",
                Instant.parse("2026-06-27T05:33:00Z")
        )));
        when(qualityEvaluationService.latestForJob("job-query-triage")).thenReturn(Optional.empty());
        LocalizationJobQueryServiceImpl service = service();

        LocalizationJobVo result = service.getJob("job-query-triage");

        assertThat(result.failureTriage()).isNotNull();
        assertThat(result.failureTriage().category()).isEqualTo(FailureTriageCategory.OPENAI_AUTH_OR_MODEL);
        assertThat(result.failureTriage().runbookCommand()).isEqualTo("scripts/demo/openai-demo-preflight.sh");
        assertThat(result.pipelineProgress()).isNotNull();
        assertThat(result.pipelineProgress().terminal()).isTrue();
    }

    private LocalizationJobQueryServiceImpl service() {
        return service(new FixedDemoOwnerIdentityService("demo-owner"));
    }

    private LocalizationJobQueryServiceImpl service(DemoOwnerIdentityService ownerIdentityService) {
        return new LocalizationJobQueryServiceImpl(
                jobRepository,
                artifactRepository,
                dispatchEventRepository,
                timelineEventRepository,
                modelCallAuditService,
                qualityEvaluationService,
                cacheService,
                failureTriageService,
                pipelineProgressService,
                ownerIdentityService
        );
    }

    private LocalizationJobVo job(String jobId, LocalizationJobStatus status) {
        return new LocalizationJobVo(
                jobId,
                "video-" + jobId,
                "zh-CN",
                null,
                status,
                Instant.parse("2026-06-27T05:30:00Z"),
                null,
                null,
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                emptyUsage(),
                new JobCacheSummaryVo(0, 0, 0),
                List.of(),
                null,
                null,
                null
        );
    }

    private JobUsageSummaryVo emptyUsage() {
        return new JobUsageSummaryVo(0, 0, 0, BigDecimal.ZERO, null, null, null, null);
    }

    private record FixedDemoOwnerIdentityService(String ownerId) implements DemoOwnerIdentityService {

        @Override
        public String currentOwnerId() {
            return ownerId;
        }
    }
}
