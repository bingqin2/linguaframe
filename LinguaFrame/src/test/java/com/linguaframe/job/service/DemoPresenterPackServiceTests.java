package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoPresenterPackServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPresenterPackServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-28T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsMetadataOnlyPresenterPackFromSameSourceRunsAndEvidenceRoutes() {
        LocalizationJobVo anchor = job("job-showcase", "tears-showcase", LocalizationJobStatus.COMPLETED, 94,
                4, "0.04000000", 1, 9, 2, Instant.parse("2026-06-28T11:00:00Z"));
        DemoRunMatrixVo matrix = new DemoRunMatrixVo(
                "job-showcase",
                "video-tears",
                Instant.parse("2026-06-28T11:30:00Z"),
                List.of(
                        matrixJob("job-showcase", "tears-showcase", LocalizationJobStatus.COMPLETED, 94,
                                "0.04000000", 4, 2, true),
                        matrixJob("job-baseline", "quick-baseline", LocalizationJobStatus.COMPLETED, 82,
                                "0.01000000", 2, 0, true),
                        matrixJob("job-low", "concise-review", LocalizationJobStatus.COMPLETED, 88,
                                "0.00500000", 2, 1, false)
                ),
                "job-baseline",
                "job-showcase",
                "job-low"
        );
        DemoPresenterPackService service = new DemoPresenterPackServiceImpl(
                new SingleJobQueryService(anchor),
                new StaticDemoRunMatrixService(matrix),
                new ReadyManifestService(),
                FIXED_CLOCK
        );

        DemoPresenterPackVo pack = service.buildPresenterPack("job-showcase");

        assertThat(pack.anchorJobId()).isEqualTo("job-showcase");
        assertThat(pack.videoId()).isEqualTo("video-tears");
        assertThat(pack.generatedAt()).isEqualTo(Instant.parse("2026-06-28T12:00:00Z"));
        assertThat(pack.headline()).contains("tears-showcase", "zh-CN");
        assertThat(pack.readinessStatus()).isEqualTo("READY");
        assertThat(pack.recommendedBaselineJobId()).isEqualTo("job-baseline");
        assertThat(pack.bestQualityJobId()).isEqualTo("job-showcase");
        assertThat(pack.lowestCostJobId()).isEqualTo("job-low");
        assertThat(pack.runs()).extracting(run -> run.jobId())
                .containsExactly("job-showcase", "job-baseline", "job-low");
        assertThat(pack.runs().get(0).roles()).containsExactly("ANCHOR", "BEST_QUALITY");
        assertThat(pack.runs().get(1).roles()).containsExactly("RECOMMENDED_BASELINE");
        assertThat(pack.runs().get(2).roles()).containsExactly("LOWEST_COST");
        assertThat(pack.runs().get(0).handoffReady()).isTrue();
        assertThat(pack.downloads()).extracting(download -> download.kind())
                .contains(
                        "DIAGNOSTICS_JSON",
                        "EVIDENCE_MARKDOWN",
                        "EVIDENCE_BUNDLE",
                        "QUALITY_EVIDENCE_MARKDOWN",
                        "DELIVERY_MANIFEST_MARKDOWN",
                        "HANDOFF_PACKAGE",
                        "DEMO_RUN_PACKAGE",
                        "AI_AUDIT_PACKAGE",
                        "ARTIFACT_ARCHIVE",
                        "SOURCE_MEDIA"
                );
        assertThat(pack.downloads()).extracting(download -> download.url())
                .contains(
                        "/api/jobs/job-showcase/demo-run-package/download",
                        "/api/jobs/job-showcase/ai-audit-package/download",
                        "/api/media/uploads/video-tears/source/download"
                );
        assertThat(pack.presenterNotesMarkdown()).contains("# LinguaFrame Demo Presenter Pack");
        assertThat(pack.presenterNotesMarkdown()).contains("- Anchor job: job-showcase");
        assertThat(pack.presenterNotesMarkdown()).contains("- Recommended baseline: job-baseline");
        assertThat(pack.presenterNotesMarkdown()).contains("- Best quality: job-showcase");
        assertThat(pack.presenterNotesMarkdown()).contains("- Lowest cost: job-low");
        assertThat(pack.presenterNotesMarkdown()).doesNotContain("raw transcript text");
        assertThat(pack.presenterNotesMarkdown()).doesNotContain("/Users/example");
        assertThat(pack.presenterNotesMarkdown()).doesNotContain("sk-test");
        assertThat(pack.presenterNotesMarkdown()).doesNotContain("provider payload");
    }

    @Test
    void marksPresenterPackNeedsAttentionWhenAnchorOrManifestIsNotReady() {
        LocalizationJobVo anchor = job("job-failed", "tears-showcase", LocalizationJobStatus.FAILED, null,
                1, "0.00500000", 0, 2, 0, Instant.parse("2026-06-28T10:00:00Z"));
        DemoRunMatrixVo matrix = new DemoRunMatrixVo(
                "job-failed",
                "video-tears",
                Instant.parse("2026-06-28T11:30:00Z"),
                List.of(matrixJob("job-failed", "tears-showcase", LocalizationJobStatus.FAILED, null,
                        "0.00500000", 1, 0, false)),
                null,
                null,
                null
        );
        DemoPresenterPackService service = new DemoPresenterPackServiceImpl(
                new SingleJobQueryService(anchor),
                new StaticDemoRunMatrixService(matrix),
                new NotReadyManifestService(),
                FIXED_CLOCK
        );

        DemoPresenterPackVo pack = service.buildPresenterPack("job-failed");

        assertThat(pack.readinessStatus()).isEqualTo("NEEDS_ATTENTION");
        assertThat(pack.runs().get(0).roles()).containsExactly("ANCHOR");
        assertThat(pack.presenterNotesMarkdown()).contains("- Readiness: NEEDS_ATTENTION");
    }

    private static DemoRunMatrixJobVo matrixJob(
            String jobId,
            String demoProfileId,
            LocalizationJobStatus status,
            Integer qualityScore,
            String estimatedCostUsd,
            int modelCallCount,
            int providerCacheHitCount,
            boolean handoffReady
    ) {
        return new DemoRunMatrixJobVo(
                jobId,
                "video-tears",
                "tears.mp4",
                "zh-CN",
                demoProfileId,
                "verse",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                status,
                Instant.parse("2026-06-28T10:00:00Z"),
                status == LocalizationJobStatus.COMPLETED ? Instant.parse("2026-06-28T10:05:00Z") : null,
                status == LocalizationJobStatus.FAILED ? "TARGET_SUBTITLE_EXPORT" : null,
                status == LocalizationJobStatus.FAILED
                        ? "raw transcript text /Users/example sk-test provider payload"
                        : null,
                0,
                qualityScore,
                qualityScore == null ? null : "GOOD",
                modelCallCount,
                status == LocalizationJobStatus.FAILED ? 1 : 0,
                new BigDecimal(estimatedCostUsd),
                0,
                8,
                providerCacheHitCount,
                handoffReady
        );
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
                "GOOD",
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
                "video-tears",
                "zh-CN",
                "verse",
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
                status == LocalizationJobStatus.FAILED
                        ? com.linguaframe.job.domain.enums.LocalizationJobStage.TARGET_SUBTITLE_EXPORT
                        : null,
                status == LocalizationJobStatus.FAILED ? "raw transcript text /Users/example sk-test provider payload" : null,
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

    private record SingleJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            if (job.jobId().equals(jobId)) {
                return job;
            }
            throw new IllegalArgumentException("Unknown job " + jobId);
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticDemoRunMatrixService(DemoRunMatrixVo matrix) implements DemoRunMatrixService {

        @Override
        public DemoRunMatrixVo buildMatrix(String anchorJobId, Integer limit) {
            return matrix;
        }
    }

    private static class ReadyManifestService implements DeliveryManifestService {

        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return manifest(jobId, true);
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class NotReadyManifestService implements DeliveryManifestService {

        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return manifest(jobId, false);
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static DeliveryManifestVo manifest(String jobId, boolean handoffReady) {
        return new DeliveryManifestVo(
                jobId,
                "video-tears",
                "zh-CN",
                "STANDARD",
                0,
                "",
                "OFF",
                "tears-showcase",
                handoffReady ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.FAILED,
                Instant.parse("2026-06-28T11:59:00Z"),
                handoffReady,
                handoffReady ? 3 : 0,
                false,
                handoffReady ? 8 : 2,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
