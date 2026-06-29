package com.linguaframe.operator.service;

import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.JobTimelineEventRepository;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.repository.ModelCallRepository;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ModelUsageLedgerServiceTests {

    @Autowired
    private ModelUsageLedgerService ledgerService;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private ModelCallRepository modelCallRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

    @Autowired
    private JobTimelineEventRepository timelineEventRepository;

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
    void buildsEmptyLedgerWhenNoModelCallsExist() {
        var ledger = ledgerService.ledger(null);

        assertThat(ledger.limit()).isEqualTo(20);
        assertThat(ledger.summary().ledgerStatus()).isEqualTo("EMPTY");
        assertThat(ledger.summary().modelCallCount()).isZero();
        assertThat(ledger.summary().estimatedCostUsd()).isEqualByComparingTo("0.00000000");
        assertThat(ledger.summary().recommendedNextAction()).contains("Run a demo job");
        assertThat(ledger.recentCalls()).isEmpty();
        assertThat(ledger.safetyNotes()).anyMatch(note -> note.contains("Raw media object keys"));
    }

    @Test
    void buildsReadyLedgerFromRecentSuccessfulModelCallsArtifactsAndCacheHits() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");
        createJob("ledger-video-ready", "ledger-job-ready", "ready.mp4", LocalizationJobStatus.COMPLETED, base);
        saveModelCall("ledger-call-translation", "ledger-job-ready", ModelCallOperation.TRANSLATION,
                ModelCallStatus.SUCCEEDED, new BigDecimal("0.00012000"), 150L, base.plusSeconds(10));
        saveModelCall("ledger-call-quality", "ledger-job-ready", ModelCallOperation.EVALUATION,
                ModelCallStatus.SUCCEEDED, new BigDecimal("0.00008000"), 90L, base.plusSeconds(20));
        saveArtifact("ledger-generated", "ledger-job-ready", false, null, base.plusSeconds(30));
        saveProviderCacheHit("ledger-cache-hit", "ledger-job-ready", base.plusSeconds(40));

        var ledger = ledgerService.ledger(5);

        assertThat(ledger.summary().ledgerStatus()).isEqualTo("READY");
        assertThat(ledger.summary().jobCount()).isEqualTo(1);
        assertThat(ledger.summary().modelCallCount()).isEqualTo(2);
        assertThat(ledger.summary().failedModelCallCount()).isZero();
        assertThat(ledger.summary().providerCacheHitCount()).isEqualTo(1);
        assertThat(ledger.summary().generatedArtifactCount()).isEqualTo(1);
        assertThat(ledger.summary().totalLatencyMs()).isEqualTo(240L);
        assertThat(ledger.summary().estimatedCostUsd()).isEqualByComparingTo("0.00020000");
        assertThat(ledger.jobs()).singleElement().satisfies(job -> {
            assertThat(job.jobId()).isEqualTo("ledger-job-ready");
            assertThat(job.modelCallCount()).isEqualTo(2);
            assertThat(job.safeLinks()).contains("/api/jobs/ledger-job-ready/ai-audit-package/download");
        });
        assertThat(ledger.operations()).extracting("operation")
                .containsExactly("EVALUATION", "TRANSLATION");
        assertThat(ledger.recentCalls().getFirst().modelCallId()).isEqualTo("ledger-call-quality");
    }

    @Test
    void marksLedgerBlockedWhenRecentFailureRateIsHigh() {
        Instant base = Instant.parse("2026-06-28T09:00:00Z");
        createJob("ledger-video-blocked", "ledger-job-blocked", "blocked.mp4", LocalizationJobStatus.FAILED, base);
        saveModelCall("ledger-call-ok", "ledger-job-blocked", ModelCallOperation.TRANSCRIPTION,
                ModelCallStatus.SUCCEEDED, new BigDecimal("0.00001000"), 100L, base.plusSeconds(1));
        saveModelCall("ledger-call-failed-1", "ledger-job-blocked", ModelCallOperation.TRANSLATION,
                ModelCallStatus.FAILED, new BigDecimal("0.00002000"), 200L, base.plusSeconds(2));
        saveModelCall("ledger-call-failed-2", "ledger-job-blocked", ModelCallOperation.EVALUATION,
                ModelCallStatus.FAILED, new BigDecimal("0.00003000"), 300L, base.plusSeconds(3));

        var ledger = ledgerService.ledger(10);

        assertThat(ledger.summary().ledgerStatus()).isEqualTo("BLOCKED");
        assertThat(ledger.summary().failureRatePercent()).isEqualByComparingTo("66.67");
        assertThat(ledger.summary().recommendedNextAction()).contains("OpenAI preflight");
        assertThat(ledger.recentCalls())
                .filteredOn(call -> "FAILED".equals(call.status()))
                .allSatisfy(call -> assertThat(call.safeErrorSummary()).isEqualTo("provider failed"));
    }

    @Test
    void rendersMarkdownEvidenceWithoutRawMediaPaths() {
        Instant base = Instant.parse("2026-06-28T10:00:00Z");
        createJob("ledger-video-markdown", "ledger-job-markdown", "markdown.mp4", LocalizationJobStatus.COMPLETED, base);
        saveModelCall("ledger-call-markdown", "ledger-job-markdown", ModelCallOperation.TRANSLATION,
                ModelCallStatus.SUCCEEDED, new BigDecimal("0.00001000"), 100L, base.plusSeconds(1));

        String markdown = ledgerService.ledgerMarkdown(10);

        assertThat(markdown).contains("LinguaFrame Model Usage Ledger");
        assertThat(markdown).contains("ledger-job-markdown");
        assertThat(markdown).contains("/api/jobs/ledger-job-markdown/demo-run-package/download");
        assertThat(markdown).doesNotContain("source-videos/ledger-video-markdown");
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
                "source-videos/" + videoId + "/" + filename,
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(jobId, videoId, "zh-CN", status, createdAt));
    }

    private void saveModelCall(
            String id,
            String jobId,
            ModelCallOperation operation,
            ModelCallStatus status,
            BigDecimal estimatedCostUsd,
            long latencyMs,
            Instant createdAt
    ) {
        modelCallRepository.save(new ModelCallRecord(
                id,
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                operation,
                ModelCallProvider.OPENAI,
                "gpt-test",
                "openai-" + operation.name().toLowerCase() + "-v1",
                status,
                latencyMs,
                100,
                50,
                null,
                null,
                "input summary",
                status == ModelCallStatus.SUCCEEDED ? "output summary" : null,
                "demo-owner",
                estimatedCostUsd,
                status == ModelCallStatus.FAILED ? "provider failed" : null,
                createdAt
        ));
    }

    private void saveArtifact(
            String id,
            String jobId,
            boolean cacheHit,
            String sourceArtifactId,
            Instant createdAt
    ) {
        artifactRepository.save(new JobArtifactRecord(
                id,
                jobId,
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/" + jobId + "/" + id + "/burned-video.mp4",
                "burned-video.mp4",
                "video/mp4",
                456L,
                "hash-" + id,
                cacheHit,
                sourceArtifactId,
                createdAt
        ));
    }

    private void saveProviderCacheHit(String id, String jobId, Instant occurredAt) {
        timelineEventRepository.save(new JobTimelineEventRecord(
                id,
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                JobTimelineEventStatus.CACHE_HIT,
                "Reused cached provider result.",
                null,
                null,
                occurredAt
        ));
    }
}
