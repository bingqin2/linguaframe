package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackRunVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoShareSheetServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoShareSheetServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-28T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void buildsReadyShareSheetWithCuratedOutcomeAndDownloadLinks() {
        LocalizationJobVo job = job("job-share-ready", LocalizationJobStatus.COMPLETED, 92, 5,
                "0.04500000", 2, 9, 3);
        DemoPresenterPackVo presenterPack = presenterPack("job-share-ready", "video-share", "READY");
        DemoShareSheetService service = new DemoShareSheetServiceImpl(
                new SingleJobQueryService(job),
                new ReadyManifestService(),
                new StaticPresenterPackService(presenterPack),
                FIXED_CLOCK
        );

        DemoShareSheetVo sheet = service.buildShareSheet("job-share-ready");

        assertThat(sheet.jobId()).isEqualTo("job-share-ready");
        assertThat(sheet.videoId()).isEqualTo("video-share");
        assertThat(sheet.generatedAt()).isEqualTo(Instant.parse("2026-06-28T12:00:00Z"));
        assertThat(sheet.readiness()).isEqualTo("READY");
        assertThat(sheet.headline()).isEqualTo("tears-showcase demo to zh-CN");
        assertThat(sheet.summary()).contains("COMPLETED", "zh-CN", "92");
        assertThat(sheet.outcomeBullets()).contains(
                "Status: COMPLETED",
                "Quality score: 92 (GOOD)",
                "Model calls: 5, estimated cost: 0.04500000 USD",
                "Generated artifacts: 9, provider cache hits: 3"
        );
        assertThat(sheet.recommendedNextAction()).isEqualTo("Open the demo run package or reviewed handoff package for reviewer delivery.");
        assertThat(sheet.links()).extracting(link -> link.kind())
                .containsExactly(
                        "DEMO_RUN_PACKAGE",
                        "HANDOFF_PACKAGE",
                        "EVIDENCE_BUNDLE",
                        "QUALITY_EVIDENCE_MARKDOWN",
                        "DELIVERY_MANIFEST_MARKDOWN",
                        "ARTIFACT_ARCHIVE",
                        "SOURCE_MEDIA"
                );
        assertThat(sheet.markdown()).contains("# tears-showcase demo to zh-CN");
        assertThat(sheet.markdown()).contains("- Status: COMPLETED");
        assertThat(sheet.markdown()).contains("- Demo run package: /api/jobs/job-share-ready/demo-run-package/download");
        assertThat(sheet.markdown()).doesNotContain("raw transcript text");
        assertThat(sheet.markdown()).doesNotContain("/Users/example");
        assertThat(sheet.markdown()).doesNotContain("sk-test");
        assertThat(sheet.markdown()).doesNotContain("provider payload");
    }

    @Test
    void marksIncompleteShareSheetNeedsAttentionWithNextAction() {
        LocalizationJobVo job = job("job-share-failed", LocalizationJobStatus.FAILED, null, 1,
                "0.00500000", 0, 2, 0);
        DemoPresenterPackVo presenterPack = presenterPack("job-share-failed", "video-share", "NEEDS_ATTENTION");
        DemoShareSheetService service = new DemoShareSheetServiceImpl(
                new SingleJobQueryService(job),
                new NotReadyManifestService(),
                new StaticPresenterPackService(presenterPack),
                FIXED_CLOCK
        );

        DemoShareSheetVo sheet = service.buildShareSheet("job-share-failed");

        assertThat(sheet.readiness()).isEqualTo("NEEDS_ATTENTION");
        assertThat(sheet.summary()).contains("FAILED");
        assertThat(sheet.outcomeBullets()).contains("Quality score: N/A", "Handoff ready: false");
        assertThat(sheet.recommendedNextAction()).isEqualTo("Review diagnostics and failure triage before sharing this run.");
        assertThat(sheet.markdown()).contains("- Readiness: NEEDS_ATTENTION");
    }

    @Test
    void rendersDownloadableMarkdownFromTheSameSafeContract() {
        LocalizationJobVo job = job("job-share-markdown", LocalizationJobStatus.COMPLETED, 88, 3,
                "0.02000000", 1, 6, 1);
        DemoShareSheetService service = new DemoShareSheetServiceImpl(
                new SingleJobQueryService(job),
                new ReadyManifestService(),
                new StaticPresenterPackService(presenterPack("job-share-markdown", "video-share", "READY")),
                FIXED_CLOCK
        );

        String markdown = service.buildMarkdownShareSheet("job-share-markdown");

        assertThat(markdown).startsWith("# tears-showcase demo to zh-CN");
        assertThat(markdown).contains("## Outcome");
        assertThat(markdown).contains("## Safe Links");
        assertThat(markdown).contains("/api/jobs/job-share-markdown/demo-share-sheet/markdown/download");
        assertThat(markdown).doesNotContain("raw transcript text");
        assertThat(markdown).doesNotContain("/Users/example");
        assertThat(markdown).doesNotContain("sk-test");
        assertThat(markdown).doesNotContain("provider payload");
    }

    private static LocalizationJobVo job(
            String jobId,
            LocalizationJobStatus status,
            Integer qualityScore,
            int modelCalls,
            String estimatedCostUsd,
            int cacheHits,
            int generatedArtifacts,
            int providerCacheHits
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
                Instant.parse("2026-06-28T11:30:00Z")
        );
        return new LocalizationJobVo(
                jobId,
                "video-share",
                "zh-CN",
                "verse",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "OFF",
                "tears-showcase",
                status,
                Instant.parse("2026-06-28T11:00:00Z"),
                Instant.parse("2026-06-28T11:00:01Z"),
                status == LocalizationJobStatus.COMPLETED ? Instant.parse("2026-06-28T11:08:00Z") : null,
                status == LocalizationJobStatus.FAILED ? Instant.parse("2026-06-28T11:04:00Z") : null,
                status == LocalizationJobStatus.FAILED
                        ? com.linguaframe.job.domain.enums.LocalizationJobStage.TARGET_SUBTITLE_EXPORT
                        : null,
                status == LocalizationJobStatus.FAILED ? "raw transcript text /Users/example sk-test provider payload" : null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(modelCalls, status == LocalizationJobStatus.FAILED ? 1 : 0, 1200L,
                        new BigDecimal(estimatedCostUsd), 100, 80, null, 480),
                new JobCacheSummaryVo(cacheHits, generatedArtifacts, providerCacheHits),
                List.of(),
                quality,
                null,
                null
        );
    }

    private static DemoPresenterPackVo presenterPack(String jobId, String videoId, String readiness) {
        return new DemoPresenterPackVo(
                jobId,
                videoId,
                Instant.parse("2026-06-28T11:59:00Z"),
                "tears-showcase demo to zh-CN",
                readiness,
                "job-baseline",
                jobId,
                "job-low-cost",
                List.of(new DemoPresenterPackRunVo(
                        jobId,
                        "tears-showcase",
                        "COMPLETED".equals(readiness) ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.FAILED,
                        Instant.parse("2026-06-28T11:08:00Z"),
                        readiness.equals("READY") ? 92 : null,
                        new BigDecimal("0.04500000"),
                        5,
                        3,
                        readiness.equals("READY"),
                        List.of("ANCHOR", "BEST_QUALITY")
                )),
                List.of(
                        link("DIAGNOSTICS_JSON", "Diagnostics JSON", "/api/jobs/%s/diagnostics/download".formatted(jobId)),
                        link("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(jobId)),
                        link("HANDOFF_PACKAGE", "Reviewed handoff package", "/api/jobs/%s/handoff-package/download".formatted(jobId)),
                        link("EVIDENCE_BUNDLE", "Evidence bundle", "/api/jobs/%s/evidence/bundle/download".formatted(jobId)),
                        link("QUALITY_EVIDENCE_MARKDOWN", "Quality evidence Markdown", "/api/jobs/%s/quality-evaluation/evidence/markdown/download".formatted(jobId)),
                        link("DELIVERY_MANIFEST_MARKDOWN", "Delivery manifest Markdown", "/api/jobs/%s/delivery-manifest/markdown/download".formatted(jobId)),
                        link("ARTIFACT_ARCHIVE", "Artifact archive", "/api/jobs/%s/artifacts/archive/download".formatted(jobId)),
                        link("SOURCE_MEDIA", "Source media", "/api/media/uploads/%s/source/download".formatted(videoId))
                ),
                "# LinguaFrame Demo Presenter Pack\nraw transcript text /Users/example sk-test provider payload\n"
        );
    }

    private static DemoPresenterPackDownloadVo link(String kind, String label, String url) {
        return new DemoPresenterPackDownloadVo(kind, label, url);
    }

    private record SingleJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticPresenterPackService(DemoPresenterPackVo presenterPack) implements DemoPresenterPackService {

        @Override
        public DemoPresenterPackVo buildPresenterPack(String jobId) {
            return presenterPack;
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
                "video-share",
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
                handoffReady,
                handoffReady ? 8 : 2,
                List.of(),
                List.of(),
                List.of()
        );
    }
}
