package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoPresenterPackService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.service.impl.PrivateDemoEvidenceGalleryServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDemoEvidenceGalleryServiceTests {

    private final StubLocalizationJobQueryService queryService = new StubLocalizationJobQueryService();
    private final StubDeliveryManifestService manifestService = new StubDeliveryManifestService();
    private final StubDemoPresenterPackService presenterPackService = new StubDemoPresenterPackService();
    private final PrivateDemoEvidenceGalleryService service = new PrivateDemoEvidenceGalleryServiceImpl(
            queryService,
            manifestService,
            presenterPackService
    );

    @Test
    void returnsEmptyGalleryWhenNoCompletedJobsExist() {
        queryService.jobs = List.of();

        PrivateDemoEvidenceGalleryVo gallery = service.evidenceGallery(20);

        assertThat(gallery.overallStatus()).isEqualTo("EMPTY");
        assertThat(gallery.completedJobCount()).isZero();
        assertThat(gallery.handoffReadyCount()).isZero();
        assertThat(gallery.recommendedJobId()).isNull();
        assertThat(gallery.jobs()).isEmpty();
        assertThat(gallery.galleryDownloads()).isEmpty();
        assertThat(gallery.galleryNotesMarkdown())
                .contains("LinguaFrame Private Demo Evidence Gallery")
                .contains("No completed demo jobs are available yet.");
    }

    @Test
    void buildsGalleryWithRecommendedHandoffReadyRunAndSafeDownloads() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");
        queryService.jobs = List.of(
                summary("job-low-cost", "video-demo", "tears-low.mp4", "quick-baseline", base, new BigDecimal("0.10")),
                summary("job-best-quality", "video-demo", "tears-best.mp4", "tears-showcase", base.plusSeconds(60), new BigDecimal("0.40"))
        );
        queryService.details.put("job-low-cost", detail("job-low-cost", "video-demo", "quick-baseline", 83, "PASS", new BigDecimal("0.10"), 3, 2, base));
        queryService.details.put("job-best-quality", detail("job-best-quality", "video-demo", "tears-showcase", 94, "EXCELLENT", new BigDecimal("0.40"), 5, 1, base.plusSeconds(60)));
        manifestService.readyJobs.add("job-low-cost");
        manifestService.readyJobs.add("job-best-quality");
        presenterPackService.readyJobs.add("job-best-quality");

        PrivateDemoEvidenceGalleryVo gallery = service.evidenceGallery(20);

        assertThat(gallery.overallStatus()).isEqualTo("READY");
        assertThat(gallery.completedJobCount()).isEqualTo(2);
        assertThat(gallery.handoffReadyCount()).isEqualTo(2);
        assertThat(gallery.recommendedJobId()).isEqualTo("job-best-quality");
        assertThat(gallery.galleryDownloads())
                .extracting("href")
                .contains(
                        "/api/jobs/job-best-quality/demo-presenter-pack",
                        "/api/jobs/job-best-quality/demo-run-package/download"
                );
        assertThat(gallery.jobs())
                .filteredOn(job -> job.jobId().equals("job-best-quality"))
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.recommended()).isTrue();
                    assertThat(job.handoffReady()).isTrue();
                    assertThat(job.presenterPackReady()).isTrue();
                    assertThat(job.qualityScore()).isEqualTo(94);
                    assertThat(job.qualityVerdict()).isEqualTo("EXCELLENT");
                    assertThat(job.modelCallCount()).isEqualTo(5);
                    assertThat(job.providerCacheHitCount()).isEqualTo(1);
                    assertThat(job.downloads())
                            .extracting("href")
                            .contains(
                                    "/api/jobs/job-best-quality",
                                    "/api/jobs/job-best-quality/diagnostics",
                                    "/api/jobs/job-best-quality/evidence/markdown/download",
                                    "/api/jobs/job-best-quality/evidence/bundle/download",
                                    "/api/jobs/job-best-quality/quality-evaluation/evidence/markdown/download",
                                    "/api/jobs/job-best-quality/delivery-manifest/markdown/download",
                                    "/api/jobs/job-best-quality/handoff-package/download",
                                    "/api/jobs/job-best-quality/demo-run-package/download",
                                    "/api/jobs/job-best-quality/ai-audit-package/download",
                                    "/api/jobs/job-best-quality/demo-presenter-pack",
                                    "/api/videos/video-demo/source"
                            );
                });
        assertThat(gallery.galleryNotesMarkdown())
                .contains("Recommended job: job-best-quality")
                .contains("tears-showcase")
                .contains("/api/jobs/job-best-quality/demo-run-package/download");
    }

    @Test
    void marksAttentionWhenCompletedJobsAreMissingHandoffReadiness() {
        Instant base = Instant.parse("2026-06-28T08:00:00Z");
        queryService.jobs = List.of(summary("job-needs-review", "video-demo", "review.mp4", null, base, BigDecimal.ZERO));
        queryService.details.put("job-needs-review", detail("job-needs-review", "video-demo", null, null, null, BigDecimal.ZERO, 0, 0, base));

        PrivateDemoEvidenceGalleryVo gallery = service.evidenceGallery(20);

        assertThat(gallery.overallStatus()).isEqualTo("ATTENTION");
        assertThat(gallery.handoffReadyCount()).isZero();
        assertThat(gallery.recommendedJobId()).isEqualTo("job-needs-review");
        assertThat(gallery.jobs())
                .singleElement()
                .satisfies(job -> {
                    assertThat(job.handoffReady()).isFalse();
                    assertThat(job.presenterPackReady()).isFalse();
                    assertThat(job.attentionReasons()).contains("Delivery handoff is not ready.");
                });
    }

    @Test
    void keepsGalleryMetadataOnly() throws Exception {
        queryService.jobs = List.of(summary(
                "job-safe",
                "video-safe",
                "safe.mp4",
                "quick-baseline",
                Instant.parse("2026-06-28T08:00:00Z"),
                BigDecimal.ZERO
        ));
        queryService.details.put("job-safe", detail(
                "job-safe",
                "video-safe",
                "quick-baseline",
                90,
                "PASS",
                BigDecimal.ZERO,
                1,
                0,
                Instant.parse("2026-06-28T08:00:00Z")
        ));

        String json = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(service.evidenceGallery(20));

        assertThat(json)
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("accessKey")
                .doesNotContain("secretKey")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("corrected subtitle text");
    }

    private LocalizationJobSummaryVo summary(
            String jobId,
            String videoId,
            String filename,
            String demoProfileId,
            Instant createdAt,
            BigDecimal estimatedCostUsd
    ) {
        return new LocalizationJobSummaryVo(
                jobId,
                videoId,
                filename,
                "zh-CN",
                "alloy",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "BALANCED",
                demoProfileId,
                LocalizationJobStatus.COMPLETED,
                createdAt,
                createdAt.plusSeconds(1),
                createdAt.plusSeconds(30),
                null,
                null,
                null,
                0,
                estimatedCostUsd
        );
    }

    private LocalizationJobVo detail(
            String jobId,
            String videoId,
            String demoProfileId,
            Integer qualityScore,
            String verdict,
            BigDecimal cost,
            int modelCalls,
            int providerCacheHits,
            Instant createdAt
    ) {
        QualityEvaluationVo quality = qualityScore == null ? null : new QualityEvaluationVo(
                "quality-" + jobId,
                jobId,
                "zh-CN",
                qualityScore,
                verdict,
                qualityScore,
                qualityScore,
                qualityScore,
                qualityScore,
                List.of(),
                List.of(),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                createdAt.plusSeconds(20)
        );
        return new LocalizationJobVo(
                jobId,
                videoId,
                "zh-CN",
                "alloy",
                "NATURAL",
                "STANDARD",
                0,
                "",
                "BALANCED",
                demoProfileId,
                LocalizationJobStatus.COMPLETED,
                createdAt,
                createdAt.plusSeconds(1),
                createdAt.plusSeconds(30),
                null,
                null,
                null,
                0,
                null,
                0,
                null,
                List.of(),
                new JobUsageSummaryVo(modelCalls, 0, 1000L, cost, null, null, null, null),
                new JobCacheSummaryVo(0, 1, providerCacheHits),
                List.of(),
                quality,
                null,
                null
        );
    }

    private static final class StubLocalizationJobQueryService implements LocalizationJobQueryService {
        private List<LocalizationJobSummaryVo> jobs = List.of();
        private final Map<String, LocalizationJobVo> details = new HashMap<>();

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            assertThat(status).isEqualTo(LocalizationJobStatus.COMPLETED);
            return new LocalizationJobListVo(jobs, limit == null ? 20 : limit, offset == null ? 0 : offset, jobs.size());
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            LocalizationJobVo detail = details.get(jobId);
            if (detail == null) {
                throw new NoSuchElementException("Missing job detail");
            }
            return detail;
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubDeliveryManifestService implements DeliveryManifestService {
        private final java.util.Set<String> readyJobs = new java.util.HashSet<>();

        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return new DeliveryManifestVo(
                    jobId,
                    "video-" + jobId,
                    "zh-CN",
                    "STANDARD",
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T08:30:00Z"),
                    readyJobs.contains(jobId),
                    readyJobs.contains(jobId) ? 1 : 0,
                    false,
                    3,
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            return "# Manifest";
        }
    }

    private static final class StubDemoPresenterPackService implements DemoPresenterPackService {
        private final java.util.Set<String> readyJobs = new java.util.HashSet<>();

        @Override
        public DemoPresenterPackVo buildPresenterPack(String jobId) {
            if (!readyJobs.contains(jobId)) {
                throw new NoSuchElementException("Presenter pack not ready");
            }
            return new DemoPresenterPackVo(
                    jobId,
                    "video-" + jobId,
                    Instant.parse("2026-06-28T08:30:00Z"),
                    "Ready",
                    "READY",
                    jobId,
                    jobId,
                    jobId,
                    List.of(),
                    List.of(),
                    "# Presenter notes"
            );
        }
    }
}
