package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryDownloadVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryJobVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalStepVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.impl.PrivateDemoRunArchiveServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDemoRunArchiveServiceTests {

    private final StubPrivateDemoOperationsService operationsService = new StubPrivateDemoOperationsService();
    private final StubPrivateDemoLaunchRehearsalService launchRehearsalService = new StubPrivateDemoLaunchRehearsalService();
    private final StubPrivateDemoEvidenceGalleryService evidenceGalleryService = new StubPrivateDemoEvidenceGalleryService();
    private final PrivateDemoRunArchiveService service = new PrivateDemoRunArchiveServiceImpl(
            operationsService,
            launchRehearsalService,
            evidenceGalleryService
    );

    @Test
    void buildsReadyArchiveFromOperationsLaunchAndRecommendedGalleryJob() {
        PrivateDemoRunArchiveVo archive = service.runArchive();

        assertThat(archive.overallStatus()).isEqualTo("READY");
        assertThat(archive.recommendedJobId()).isEqualTo("job-gallery-best");
        assertThat(archive.recommendedVideoId()).isEqualTo("video-gallery");
        assertThat(archive.recommendedProfileId()).isEqualTo("tears-showcase");
        assertThat(archive.recommendedReadiness()).isEqualTo("READY");
        assertThat(archive.operationsOverallStatus()).isEqualTo("READY");
        assertThat(archive.launchOverallStatus()).isEqualTo("READY");
        assertThat(archive.launchRecommendedNextStep()).isEqualTo("operations-report-export");
        assertThat(archive.galleryCompletedJobCount()).isEqualTo(2);
        assertThat(archive.galleryHandoffReadyCount()).isEqualTo(1);
        assertThat(archive.candidates())
                .extracting("jobId")
                .containsExactly("job-gallery-best", "job-gallery-baseline");
        assertThat(archive.candidates().getFirst())
                .satisfies(candidate -> {
                    assertThat(candidate.profileId()).isEqualTo("tears-showcase");
                    assertThat(candidate.status()).isEqualTo("COMPLETED");
                    assertThat(candidate.readiness()).isEqualTo("READY");
                    assertThat(candidate.qualityScore()).isEqualTo(94);
                    assertThat(candidate.estimatedCostUsd()).isEqualByComparingTo("0.40000000");
                    assertThat(candidate.modelCallCount()).isEqualTo(5);
                    assertThat(candidate.providerCacheHitCount()).isEqualTo(1);
                    assertThat(candidate.handoffReady()).isTrue();
                    assertThat(candidate.roles()).contains("RECOMMENDED", "HANDOFF_READY");
                });
        assertThat(archive.archiveLinks())
                .extracting("href")
                .contains(
                        "/api/operator/private-demo/operations",
                        "/api/operator/private-demo/launch-rehearsal",
                        "/api/operator/private-demo/evidence-gallery",
                        "/api/jobs/job-gallery-best/demo-presenter-pack",
                        "/api/jobs/job-gallery-best/demo-run-package/download",
                        "/api/jobs/job-gallery-best/handoff-package/download",
                        "/api/jobs/job-gallery-best/ai-audit-package/download",
                        "/api/jobs/job-gallery-best/evidence/bundle/download",
                        "/api/jobs/job-gallery-best/diagnostics",
                        "/api/jobs/job-gallery-best/artifacts/archive/download"
                );
        assertThat(archive.archiveNotesMarkdown())
                .contains("LinguaFrame Private Demo Run Archive")
                .contains("Recommended job: job-gallery-best")
                .contains("Demo run package: /api/jobs/job-gallery-best/demo-run-package/download")
                .contains("Private demo run archive is metadata-only");
    }

    @Test
    void returnsAttentionArchiveWhenNoRecommendedCompletedJobExists() {
        evidenceGalleryService.gallery = new PrivateDemoEvidenceGalleryVo(
                Instant.parse("2026-06-28T08:00:00Z"),
                "EMPTY",
                0,
                0,
                null,
                List.of(),
                List.of(),
                "# Gallery\nraw transcript text /Users/example/private.mov private-demo-token provider payload"
        );

        PrivateDemoRunArchiveVo archive = service.runArchive();

        assertThat(archive.overallStatus()).isEqualTo("ATTENTION");
        assertThat(archive.recommendedJobId()).isNull();
        assertThat(archive.recommendedReadiness()).isEqualTo("MISSING");
        assertThat(archive.candidates()).isEmpty();
        assertThat(archive.archiveLinks())
                .extracting("href")
                .contains(
                        "/api/operator/private-demo/operations",
                        "/api/operator/private-demo/launch-rehearsal",
                        "/api/operator/private-demo/evidence-gallery"
                );
        assertThat(archive.archiveNotesMarkdown())
                .contains("No completed recommended job is available yet.")
                .doesNotContain("raw transcript text")
                .doesNotContain("/Users/example")
                .doesNotContain("private-demo-token")
                .doesNotContain("provider payload");
    }

    @Test
    void archiveJsonIsMetadataOnly() throws Exception {
        PrivateDemoRunArchiveVo archive = service.runArchive();

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(archive);

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

    private static PrivateDemoOperationsVo operationsVo(String status) {
        return new PrivateDemoOperationsVo(
                Instant.parse("2026-06-28T08:00:00Z"),
                status,
                8,
                0,
                0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static PrivateDemoLaunchRehearsalVo launch(String status) {
        return new PrivateDemoLaunchRehearsalVo(
                Instant.parse("2026-06-28T08:01:00Z"),
                status,
                10,
                0,
                0,
                "operations-report-export",
                List.of(new PrivateDemoLaunchRehearsalStepVo(
                        "operations-report-export",
                        "Operations report export",
                        "READY",
                        "Export metadata-only operations report.",
                        "scripts/demo/private-demo-operations-report.sh",
                        "/api/operator/private-demo/operations",
                        "Keep report with demo evidence.",
                        false
                )),
                List.of("/api/operator/private-demo/operations", "/api/jobs/{jobId}/demo-presenter-pack"),
                "# Launch rehearsal\nOPENAI_API_KEY /Users/example/private.mov private-demo-token provider payload"
        );
    }

    private static PrivateDemoEvidenceGalleryVo gallery() {
        Instant base = Instant.parse("2026-06-28T08:02:00Z");
        PrivateDemoEvidenceGalleryJobVo recommended = job(
                "job-gallery-best",
                "video-gallery",
                "tears-best.mp4",
                "tears-showcase",
                base,
                94,
                new BigDecimal("0.40000000"),
                5,
                1,
                true,
                true,
                true
        );
        PrivateDemoEvidenceGalleryJobVo baseline = job(
                "job-gallery-baseline",
                "video-gallery",
                "tears-baseline.mp4",
                "quick-baseline",
                base.minusSeconds(60),
                83,
                new BigDecimal("0.10000000"),
                3,
                2,
                false,
                false,
                false
        );
        return new PrivateDemoEvidenceGalleryVo(
                base,
                "READY",
                2,
                1,
                "job-gallery-best",
                List.of(recommended, baseline),
                recommended.downloads(),
                "# Gallery\nRecommended job: job-gallery-best\nraw subtitle text corrected subtitle text"
        );
    }

    private static PrivateDemoEvidenceGalleryJobVo job(
            String jobId,
            String videoId,
            String filename,
            String profileId,
            Instant completedAt,
            Integer qualityScore,
            BigDecimal cost,
            int modelCallCount,
            int providerCacheHitCount,
            boolean handoffReady,
            boolean presenterPackReady,
            boolean recommended
    ) {
        return new PrivateDemoEvidenceGalleryJobVo(
                jobId,
                videoId,
                filename,
                "zh-CN",
                profileId,
                LocalizationJobStatus.COMPLETED,
                completedAt.minusSeconds(120),
                completedAt,
                qualityScore,
                qualityScore == null ? null : "PASS",
                cost,
                modelCallCount,
                providerCacheHitCount,
                handoffReady,
                presenterPackReady,
                recommended,
                List.of(),
                List.of(
                        new PrivateDemoEvidenceGalleryDownloadVo(
                                "Demo run package",
                                "/api/jobs/" + jobId + "/demo-run-package/download",
                                "application/zip",
                                "Complete safe demo run package."
                        ),
                        new PrivateDemoEvidenceGalleryDownloadVo(
                                "Presenter pack",
                                "/api/jobs/" + jobId + "/demo-presenter-pack",
                                "application/json",
                                "Presenter-facing recommended evidence pack."
                        )
                )
        );
    }

    private static class StubPrivateDemoOperationsService implements PrivateDemoOperationsService {
        private PrivateDemoOperationsVo operations = operationsVo("READY");

        @Override
        public PrivateDemoOperationsVo operations() {
            return operations;
        }
    }

    private static class StubPrivateDemoLaunchRehearsalService implements PrivateDemoLaunchRehearsalService {
        private PrivateDemoLaunchRehearsalVo launch = launch("READY");

        @Override
        public PrivateDemoLaunchRehearsalVo launchRehearsal() {
            return launch;
        }
    }

    private static class StubPrivateDemoEvidenceGalleryService implements PrivateDemoEvidenceGalleryService {
        private PrivateDemoEvidenceGalleryVo gallery = gallery();

        @Override
        public PrivateDemoEvidenceGalleryVo evidenceGallery(Integer limit) {
            return gallery;
        }
    }
}
