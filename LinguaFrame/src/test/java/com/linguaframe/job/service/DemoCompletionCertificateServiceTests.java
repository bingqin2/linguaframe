package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackDownloadVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackRunVo;
import com.linguaframe.job.domain.vo.DemoPresenterPackVo;
import com.linguaframe.job.domain.vo.DemoReplayCardCommandVo;
import com.linguaframe.job.domain.vo.DemoReplayCardLinkVo;
import com.linguaframe.job.domain.vo.DemoReplayCardSettingVo;
import com.linguaframe.job.domain.vo.DemoReplayCardVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixJobVo;
import com.linguaframe.job.domain.vo.DemoRunMatrixVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotLinkVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotSectionVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.DemoShareSheetLinkVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.impl.DemoCompletionCertificateServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCompletionCertificateServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-29T11:30:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsReadyCompletionCertificateFromExistingEvidenceSurfaces() {
        LocalizationJobVo job = job("job-certificate", LocalizationJobStatus.COMPLETED);
        DemoCompletionCertificateService service = service(job, true, "READY", "READY", "READY", "READY", matrix(job));

        DemoCompletionCertificateVo certificate = service.buildCertificate("job-certificate");

        assertThat(certificate.jobId()).isEqualTo("job-certificate");
        assertThat(certificate.videoId()).isEqualTo("video-certificate");
        assertThat(certificate.generatedAt()).isEqualTo(NOW);
        assertThat(certificate.certificateStatus()).isEqualTo("READY");
        assertThat(certificate.recommendedBaselineJobId()).isEqualTo("job-baseline");
        assertThat(certificate.checks()).extracting("status").containsOnly("PASS");
        assertThat(certificate.sections()).extracting("key")
                .contains("RUN_IDENTITY", "DELIVERY", "REPRODUCIBILITY", "EVIDENCE", "COST_CACHE");
        assertThat(certificate.links()).extracting("kind")
                .contains("CERTIFICATE_JSON", "REPLAY_CARD_JSON", "DEMO_RUN_PACKAGE", "SNAPSHOT_DOWNLOAD");
        assertThat(certificate.recommendedNextAction())
                .contains("final demo handoff evidence");
        assertThat(certificate.toString())
                .doesNotContain("sk-test")
                .doesNotContain("/Users/example")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text");
    }

    @Test
    void marksCompletionCertificateNeedsAttentionForCompletedJobWithWarnings() {
        LocalizationJobVo job = job("job-warning", LocalizationJobStatus.COMPLETED);
        DemoCompletionCertificateService service = service(job, false, "NEEDS_ATTENTION", "READY", "READY", "READY", matrix(job));

        DemoCompletionCertificateVo certificate = service.buildCertificate("job-warning");

        assertThat(certificate.certificateStatus()).isEqualTo("NEEDS_ATTENTION");
        assertThat(certificate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("HANDOFF_READY");
                    assertThat(check.status()).isEqualTo("WARN");
                    assertThat(check.blocking()).isFalse();
                });
    }

    @Test
    void blocksCompletionCertificateWhenJobIsNotCompleted() {
        LocalizationJobVo job = job("job-failed", LocalizationJobStatus.FAILED);
        DemoCompletionCertificateService service = service(job, false, "NEEDS_ATTENTION", "NEEDS_ATTENTION", "NEEDS_ATTENTION", "NEEDS_ATTENTION", matrix(job));

        DemoCompletionCertificateVo certificate = service.buildCertificate("job-failed");

        assertThat(certificate.certificateStatus()).isEqualTo("BLOCKED");
        assertThat(certificate.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("JOB_COMPLETED");
                    assertThat(check.status()).isEqualTo("FAIL");
                    assertThat(check.blocking()).isTrue();
                });
        assertThat(certificate.safetyNotes())
                .contains("The selected job is not completed, so this certificate cannot be used as final demo proof.");
    }

    private static DemoCompletionCertificateService service(
            LocalizationJobVo job,
            boolean handoffReady,
            String presenterReadiness,
            String replayReadiness,
            String shareReadiness,
            String snapshotReadiness,
            DemoRunMatrixVo matrix
    ) {
        return new DemoCompletionCertificateServiceImpl(
                new StaticQueryService(job),
                new StaticDeliveryManifestService(manifest(job, handoffReady)),
                new StaticPresenterPackService(presenterPack(job, presenterReadiness)),
                new StaticReplayCardService(replayCard(job, replayReadiness, matrix)),
                new StaticShareSheetService(shareSheet(job, shareReadiness)),
                new StaticSnapshotService(snapshot(job, snapshotReadiness)),
                new StaticMatrixService(matrix),
                CLOCK
        );
    }

    private static LocalizationJobVo job(String jobId, LocalizationJobStatus status) {
        return new LocalizationJobVo(
                jobId,
                "video-certificate",
                "zh-CN",
                "verse",
                "FORMAL",
                "HIGH_CONTRAST",
                3,
                "abc123",
                "BALANCED",
                "tears-showcase",
                status,
                NOW.minusSeconds(120),
                NOW.minusSeconds(90),
                status == LocalizationJobStatus.COMPLETED ? NOW.minusSeconds(10) : null,
                status == LocalizationJobStatus.FAILED ? NOW.minusSeconds(10) : null,
                null,
                status == LocalizationJobStatus.FAILED ? "Provider unavailable" : null,
                0,
                JobDispatchEventStatus.DISPATCHED,
                0,
                NOW.minusSeconds(110),
                List.of(),
                new JobUsageSummaryVo(3, 0, 4200, new BigDecimal("0.00007800"), 100, 80, new BigDecimal("45.0"), 1200),
                new JobCacheSummaryVo(1, 6, 2),
                List.of(),
                null,
                null,
                null
        );
    }

    private static DeliveryManifestVo manifest(LocalizationJobVo job, boolean handoffReady) {
        return new DeliveryManifestVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.demoProfileId(),
                job.status(),
                NOW,
                handoffReady,
                handoffReady ? 3 : 0,
                false,
                6,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static DemoPresenterPackVo presenterPack(LocalizationJobVo job, String readiness) {
        return new DemoPresenterPackVo(
                job.jobId(),
                job.videoId(),
                NOW,
                "tears-showcase demo to zh-CN",
                readiness,
                "job-baseline",
                job.jobId(),
                "job-baseline",
                List.of(new DemoPresenterPackRunVo(
                        job.jobId(),
                        job.demoProfileId(),
                        job.status(),
                        job.completedAt(),
                        91,
                        new BigDecimal("0.00007800"),
                        3,
                        2,
                        true,
                        List.of("ANCHOR", "BEST_QUALITY")
                )),
                List.of(
                        new DemoPresenterPackDownloadVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(job.jobId())),
                        new DemoPresenterPackDownloadVo("HANDOFF_PACKAGE", "Reviewed handoff package", "/api/jobs/%s/handoff-package/download".formatted(job.jobId()))
                ),
                "# LinguaFrame Demo Presenter Pack\n"
        );
    }

    private static DemoReplayCardVo replayCard(LocalizationJobVo job, String readiness, DemoRunMatrixVo matrix) {
        return new DemoReplayCardVo(
                job.jobId(),
                job.videoId(),
                NOW,
                "tears-showcase replay card to zh-CN",
                readiness,
                job.status(),
                job.targetLanguage(),
                job.demoProfileId(),
                91,
                "GOOD",
                3,
                2,
                1,
                new BigDecimal("0.00007800"),
                matrix.recommendedBaselineJobId(),
                matrix.bestQualityJobId(),
                matrix.lowestCostJobId(),
                List.of(new DemoReplayCardSettingVo("demoProfileId", "Demo profile", "tears-showcase")),
                List.of(new DemoReplayCardCommandVo(
                        "EXPORT_REPLAY_CARD",
                        "Export this replay card",
                        "LINGUAFRAME_DEMO_JOB_ID=%s scripts/demo/demo-replay-card.sh".formatted(job.jobId()),
                        "Writes JSON."
                )),
                List.of(new DemoReplayCardLinkVo("REPLAY_CARD_JSON", "Replay card JSON", "/api/jobs/%s/demo-replay-card".formatted(job.jobId()))),
                List.of("Metadata only.")
        );
    }

    private static DemoShareSheetVo shareSheet(LocalizationJobVo job, String readiness) {
        return new DemoShareSheetVo(
                job.jobId(),
                job.videoId(),
                NOW,
                readiness,
                "tears-showcase demo to zh-CN",
                "Completed demo share sheet.",
                List.of("Status: " + job.status()),
                "Open the demo run package.",
                List.of(new DemoShareSheetLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/%s/demo-run-package/download".formatted(job.jobId()))),
                "# tears-showcase demo to zh-CN\n"
        );
    }

    private static DemoRunSnapshotVo snapshot(LocalizationJobVo job, String readiness) {
        return new DemoRunSnapshotVo(
                job.jobId(),
                job.videoId(),
                job.targetLanguage(),
                job.demoProfileId(),
                NOW,
                readiness,
                "tears-showcase demo to zh-CN",
                "Static snapshot.",
                List.of(new DemoRunSnapshotSectionVo("INDEX_HTML", "Offline index", readiness, "index.html", "Self-contained index.")),
                List.of("index.html", "manifest.json", "README.md"),
                List.of(new DemoRunSnapshotLinkVo("SNAPSHOT_DOWNLOAD", "Static snapshot ZIP", "/api/jobs/%s/demo-run-snapshot/download".formatted(job.jobId()))),
                List.of("media bytes", "provider payloads"),
                "# LinguaFrame Demo Snapshot\n"
        );
    }

    private static DemoRunMatrixVo matrix(LocalizationJobVo job) {
        return new DemoRunMatrixVo(
                job.jobId(),
                job.videoId(),
                NOW,
                List.of(matrixJob(job), matrixJob(job, "job-baseline", "quick-baseline")),
                "job-baseline",
                job.jobId(),
                "job-baseline"
        );
    }

    private static DemoRunMatrixJobVo matrixJob(LocalizationJobVo job) {
        return matrixJob(job, job.jobId(), job.demoProfileId());
    }

    private static DemoRunMatrixJobVo matrixJob(LocalizationJobVo job, String jobId, String demoProfileId) {
        return new DemoRunMatrixJobVo(
                jobId,
                job.videoId(),
                "certificate.mp4",
                job.targetLanguage(),
                demoProfileId,
                job.ttsVoice(),
                job.translationStyle(),
                job.subtitleStylePreset(),
                job.translationGlossaryEntryCount(),
                job.translationGlossaryHash(),
                job.subtitlePolishingMode(),
                job.status(),
                job.createdAt(),
                job.completedAt(),
                null,
                null,
                0,
                "tears-showcase".equals(demoProfileId) ? 91 : 82,
                "GOOD",
                3,
                0,
                new BigDecimal("0.00007800"),
                1,
                6,
                2,
                true
        );
    }

    private record StaticQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
        @Override
        public com.linguaframe.job.domain.vo.LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return job;
        }

        @Override
        public com.linguaframe.job.domain.vo.JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticDeliveryManifestService(DeliveryManifestVo manifest) implements DeliveryManifestService {
        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return manifest;
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticPresenterPackService(DemoPresenterPackVo presenterPack) implements DemoPresenterPackService {
        @Override
        public DemoPresenterPackVo buildPresenterPack(String jobId) {
            return presenterPack;
        }
    }

    private record StaticReplayCardService(DemoReplayCardVo replayCard) implements DemoReplayCardService {
        @Override
        public DemoReplayCardVo buildReplayCard(String jobId) {
            return replayCard;
        }
    }

    private record StaticShareSheetService(DemoShareSheetVo shareSheet) implements DemoShareSheetService {
        @Override
        public DemoShareSheetVo buildShareSheet(String jobId) {
            return shareSheet;
        }

        @Override
        public String buildMarkdownShareSheet(String jobId) {
            return shareSheet.markdown();
        }
    }

    private record StaticSnapshotService(DemoRunSnapshotVo snapshot) implements DemoRunSnapshotService {
        @Override
        public DemoRunSnapshotVo buildSnapshot(String jobId) {
            return snapshot;
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredDemoRunSnapshotPackageBo openSnapshotPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticMatrixService(DemoRunMatrixVo matrix) implements DemoRunMatrixService {
        @Override
        public DemoRunMatrixVo buildMatrix(String anchorJobId, Integer limit) {
            return matrix;
        }
    }
}
