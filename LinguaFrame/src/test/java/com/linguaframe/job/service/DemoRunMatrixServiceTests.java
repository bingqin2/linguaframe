package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoRunMatrixServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRunMatrixServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsSameSourceMatrixWithRecommendationsAndSafeRunMetrics() {
        LocalizationJobVo anchor = job(
                "job-tears",
                "tears-showcase",
                LocalizationJobStatus.COMPLETED,
                92,
                4,
                "0.04000000",
                2,
                8,
                1,
                Instant.parse("2026-06-28T09:00:00Z")
        );
        LocalizationJobVo baseline = job(
                "job-baseline",
                "quick-baseline",
                LocalizationJobStatus.COMPLETED,
                80,
                2,
                "0.01000000",
                0,
                8,
                0,
                Instant.parse("2026-06-28T08:00:00Z")
        );
        LocalizationJobVo concise = job(
                "job-concise",
                "concise-review",
                LocalizationJobStatus.COMPLETED,
                86,
                3,
                "0.02000000",
                1,
                8,
                1,
                Instant.parse("2026-06-28T08:30:00Z")
        );
        LocalizationJobVo failed = job(
                "job-failed",
                "tears-showcase",
                LocalizationJobStatus.FAILED,
                null,
                1,
                "0.00500000",
                0,
                2,
                0,
                Instant.parse("2026-06-28T07:30:00Z")
        );
        DemoRunMatrixService service = new DemoRunMatrixServiceImpl(
                new RecordingLocalizationJobQueryService(anchor, concise, baseline, failed),
                new RecordingDeliveryManifestService(List.of("job-tears", "job-baseline")),
                FIXED_CLOCK
        );

        DemoRunMatrixVo matrix = service.buildMatrix("job-tears", 8);

        assertThat(matrix.anchorJobId()).isEqualTo("job-tears");
        assertThat(matrix.videoId()).isEqualTo("shared-video");
        assertThat(matrix.generatedAt()).isEqualTo(Instant.parse("2026-06-28T10:00:00Z"));
        assertThat(matrix.jobs()).extracting(job -> job.jobId())
                .containsExactly("job-tears", "job-concise", "job-baseline", "job-failed");
        assertThat(matrix.recommendedBaselineJobId()).isEqualTo("job-baseline");
        assertThat(matrix.bestQualityJobId()).isEqualTo("job-tears");
        assertThat(matrix.lowestCostJobId()).isEqualTo("job-baseline");
        assertThat(matrix.jobs().get(0).qualityScore()).isEqualTo(92);
        assertThat(matrix.jobs().get(0).estimatedCostUsd()).isEqualByComparingTo("0.04000000");
        assertThat(matrix.jobs().get(0).handoffReady()).isTrue();
        assertThat(matrix.jobs().get(1).demoProfileId()).isEqualTo("concise-review");
        assertThat(matrix.jobs().get(3).qualityScore()).isNull();
        assertThat(matrix.jobs().get(3).handoffReady()).isFalse();
    }

    @Test
    void normalizesMatrixLimitToKeepDemoResponsesBounded() {
        LocalizationJobVo anchor = job("job-anchor", "quick-baseline", LocalizationJobStatus.COMPLETED, 80,
                1, "0.01000000", 0, 2, 0, Instant.parse("2026-06-28T08:00:00Z"));
        RecordingLocalizationJobQueryService queryService = new RecordingLocalizationJobQueryService(anchor);
        DemoRunMatrixService service = new DemoRunMatrixServiceImpl(
                queryService,
                new RecordingDeliveryManifestService(List.of("job-anchor")),
                FIXED_CLOCK
        );

        service.buildMatrix("job-anchor", 100);

        assertThat(queryService.lastLimit).isEqualTo(12);
    }

    private static LocalizationJobVo job(
            String jobId,
            String demoProfileId,
            LocalizationJobStatus status,
            Integer qualityScore,
            int modelCalls,
            String estimatedCostUsd,
            int cacheHits,
            int generatedArtifacts,
            int providerCacheHits,
            Instant createdAt
    ) {
        QualityEvaluationVo quality = qualityScore == null ? null : new QualityEvaluationVo(
                "quality-" + jobId,
                jobId,
                "zh-CN",
                qualityScore,
                qualityScore >= 90 ? "EXCELLENT" : "PASS",
                qualityScore,
                qualityScore,
                qualityScore,
                qualityScore,
                List.of(),
                List.of(),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(30)
        );
        return new LocalizationJobVo(
                jobId,
                "shared-video",
                "zh-CN",
                "",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                demoProfileId,
                status,
                createdAt,
                createdAt.plusSeconds(1),
                status == LocalizationJobStatus.COMPLETED ? createdAt.plusSeconds(120) : null,
                status == LocalizationJobStatus.FAILED ? createdAt.plusSeconds(80) : null,
                status == LocalizationJobStatus.FAILED ? com.linguaframe.job.domain.enums.LocalizationJobStage.TARGET_SUBTITLE_EXPORT : null,
                status == LocalizationJobStatus.FAILED ? "provider rejected request" : null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(modelCalls, status == LocalizationJobStatus.FAILED ? 1 : 0, 1500L,
                        new BigDecimal(estimatedCostUsd), 100, 80, null, 500),
                new JobCacheSummaryVo(cacheHits, generatedArtifacts, providerCacheHits),
                List.of(),
                quality,
                null,
                null
        );
    }

    private static LocalizationJobSummaryVo summary(LocalizationJobVo job) {
        return new LocalizationJobSummaryVo(
                job.jobId(),
                job.videoId(),
                job.jobId() + ".mp4",
                job.targetLanguage(),
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.demoProfileId(),
                job.status(),
                job.createdAt(),
                job.startedAt(),
                job.completedAt(),
                job.failedAt(),
                job.failureStage(),
                job.failureReason(),
                job.retryCount(),
                job.usageSummary().estimatedCostUsd()
        );
    }

    private static final class RecordingLocalizationJobQueryService implements LocalizationJobQueryService {
        private final List<LocalizationJobVo> jobs;
        private int lastLimit;

        private RecordingLocalizationJobQueryService(LocalizationJobVo... jobs) {
            this.jobs = List.of(jobs);
        }

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobListVo listJobsByVideoId(String videoId, Integer limit) {
            lastLimit = limit == null ? -1 : limit;
            List<LocalizationJobSummaryVo> summaries = jobs.stream()
                    .filter(job -> job.videoId().equals(videoId))
                    .limit(lastLimit)
                    .map(DemoRunMatrixServiceTests::summary)
                    .toList();
            return new LocalizationJobListVo(summaries, lastLimit, 0, summaries.size());
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return jobs.stream()
                    .filter(job -> job.jobId().equals(jobId))
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record RecordingDeliveryManifestService(List<String> readyJobs) implements DeliveryManifestService {

        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return new DeliveryManifestVo(
                    jobId,
                    "shared-video",
                    "zh-CN",
                    "STANDARD",
                    0,
                    "",
                    "OFF",
                    null,
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T10:00:00Z"),
                    readyJobs.contains(jobId),
                    3,
                    false,
                    8,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
