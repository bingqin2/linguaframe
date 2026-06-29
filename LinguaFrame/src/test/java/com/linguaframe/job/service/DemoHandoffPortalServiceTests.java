package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredDemoHandoffPortalPackageBo;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateCheckVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateEvidenceVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateLinkVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateCheckVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateLinkVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateSectionVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceCheckVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceLinkVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceSectionVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorLinkVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotLinkVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotSectionVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.DemoShareSheetLinkVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofCheckVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofLinkVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoHandoffPortalServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DemoHandoffPortalServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-29T14:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsReadyPortalForCompletedReviewerEvidence() throws IOException {
        DemoHandoffPortalService service = service(
                job(LocalizationJobStatus.COMPLETED, true),
                reviewerWorkspace("READY"),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                snapshot("READY"),
                shareSheet("READY"),
                monitor("READY"),
                openAiProof("READY")
        );

        DemoHandoffPortalVo portal = service.getPortal("job-portal");

        assertThat(portal.jobId()).isEqualTo("job-portal");
        assertThat(portal.videoId()).isEqualTo("video-portal");
        assertThat(portal.generatedAt()).isEqualTo(NOW);
        assertThat(portal.overallStatus()).isEqualTo("READY");
        assertThat(portal.phase()).isEqualTo("HANDOFF_PORTAL_READY");
        assertThat(portal.headline()).contains("ready");
        assertThat(portal.checks()).extracting("status").containsOnly("READY");
        assertThat(portal.sections()).extracting("title")
                .contains("Reviewer workspace", "Offline portal", "Narration audio mix", "Presentation evidence", "Safe packages");
        assertThat(portal.sections())
                .filteredOn(section -> section.key().equals("NARRATION_AUDIO_MIX"))
                .singleElement()
                .satisfies(section -> assertThat(section.facts())
                        .contains(
                                "Audio layout: TIMED_AUDIO_BED",
                                "Time aligned: true",
                                "Video mix mode: DUCKED_ORIGINAL_AUDIO",
                                "Ducking volume: 0.35"
                        ));
        assertThat(portal.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-portal/demo-handoff-portal/download",
                        "/api/jobs/job-portal/demo-reviewer-workspace/download",
                        "/api/jobs/job-portal/subtitle-review-evidence/download",
                        "/api/jobs/job-portal/narration-evidence/download",
                        "/api/jobs/job-portal/narration-workspace/generate-video",
                        "/api/jobs/job-portal/demo-run-snapshot/download",
                        "/api/jobs/job-portal/demo-run-package/download"
                );

        StoredDemoHandoffPortalPackageBo portalPackage = service.openPackage("job-portal");
        Map<String, String> entries = zipEntries(portalPackage.inputStream());
        assertThat(entries.keySet()).contains(
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
                .contains("<!doctype html>")
                .contains("LinguaFrame Demo Handoff Portal")
                .contains("HANDOFF_PORTAL_READY")
                .contains("DUCKED_ORIGINAL_AUDIO");
    }

    @Test
    void warnsWhenOptionalOpenAiProofNeedsAttention() {
        DemoHandoffPortalService service = service(
                job(LocalizationJobStatus.COMPLETED, true),
                reviewerWorkspace("READY"),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                snapshot("READY"),
                shareSheet("READY"),
                monitor("READY"),
                openAiProof("ATTENTION")
        );

        DemoHandoffPortalVo portal = service.getPortal("job-portal");

        assertThat(portal.overallStatus()).isEqualTo("ATTENTION");
        assertThat(portal.phase()).isEqualTo("HANDOFF_PORTAL_NEEDS_REVIEW");
        assertThat(portal.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("OPENAI_SMOKE_PROOF");
                    assertThat(check.status()).isEqualTo("ATTENTION");
                    assertThat(check.required()).isFalse();
                });
    }

    @Test
    void blocksWhenRequiredReviewerWorkspaceIsBlocked() {
        DemoHandoffPortalService service = service(
                job(LocalizationJobStatus.PROCESSING, false),
                reviewerWorkspace("BLOCKED"),
                acceptance("BLOCKED"),
                certificate("BLOCKED"),
                manifest(false),
                snapshot("ATTENTION"),
                shareSheet("ATTENTION"),
                monitor("ATTENTION"),
                openAiProof("ATTENTION")
        );

        DemoHandoffPortalVo portal = service.getPortal("job-portal");

        assertThat(portal.overallStatus()).isEqualTo("BLOCKED");
        assertThat(portal.phase()).isEqualTo("HANDOFF_PORTAL_BLOCKED");
        assertThat(portal.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("REVIEWER_WORKSPACE");
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.required()).isTrue();
                });
    }

    @Test
    void rendersSafeMarkdownAndPackageWithoutSecretsOrRawContent() throws IOException {
        DemoHandoffPortalService service = service(
                job(LocalizationJobStatus.COMPLETED, true),
                reviewerWorkspace("READY"),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                snapshot("READY"),
                shareSheet("READY"),
                monitor("READY"),
                openAiProof("READY")
        );

        DemoHandoffPortalVo portal = service.getPortal("job-portal");
        String markdown = service.renderMarkdown("job-portal");
        StoredDemoHandoffPortalPackageBo portalPackage = service.openPackage("job-portal");
        Map<String, String> entries = zipEntries(portalPackage.inputStream());
        String combined = markdown + "\n" + String.join("\n", entries.values());

        assertThat(markdown)
                .contains("# LinguaFrame Demo Handoff Portal")
                .contains("- Job: job-portal")
                .contains("- Overall status: READY")
                .contains("Demo reviewer workspace")
                .contains("Static handoff portal ZIP");
        assertThat(portal.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-portal/subtitle-review-evidence/download",
                        "/api/jobs/job-portal/narration-evidence/download",
                        "/api/jobs/job-portal/narration-workspace/generate-video"
                );
        assertThat(portal.packageEntries())
                .contains(
                        "Linked safe route: /api/jobs/job-portal/subtitle-review-evidence/download",
                        "Linked safe route: /api/jobs/job-portal/narration-evidence/download",
                        "Linked safe route: /api/jobs/job-portal/narration-workspace/generate-video"
                );
        assertThat(combined)
                .doesNotContain("sk-test")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("bearer token")
                .doesNotContain("/Users/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("objectKey")
                .doesNotContain("provider request payload")
                .doesNotContain("provider response body")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    private static DemoHandoffPortalService service(
            LocalizationJobVo job,
            DemoReviewerWorkspaceVo reviewerWorkspace,
            DemoAcceptanceGateVo acceptance,
            DemoCompletionCertificateVo certificate,
            DeliveryManifestVo manifest,
            DemoRunSnapshotVo snapshot,
            DemoShareSheetVo shareSheet,
            DemoRunMonitorVo monitor,
            OpenAiSmokeProofVo openAiProof
    ) {
        return new DemoHandoffPortalServiceImpl(
                new StaticLocalizationJobQueryService(job),
                new StaticReviewerWorkspaceService(reviewerWorkspace),
                new StaticAcceptanceGateService(acceptance),
                new StaticCompletionCertificateService(certificate),
                new StaticDeliveryManifestService(manifest),
                new StaticRunSnapshotService(snapshot),
                new StaticShareSheetService(shareSheet),
                new StaticRunMonitorService(monitor),
                new StaticOpenAiSmokeProofService(openAiProof),
                CLOCK
        );
    }

    private static LocalizationJobVo job(LocalizationJobStatus status, boolean includeQualityEvaluation) {
        return new LocalizationJobVo(
                "job-portal",
                "video-portal",
                "zh-CN",
                "verse",
                "FORMAL",
                "HIGH_CONTRAST",
                1,
                "glossary-hash",
                "BALANCED",
                "tears-showcase",
                status,
                NOW.minusSeconds(300),
                NOW.minusSeconds(280),
                status == LocalizationJobStatus.COMPLETED ? NOW.minusSeconds(30) : null,
                null,
                null,
                "provider request payload provider response body raw transcript text raw subtitle text sk-test /Users/example job-artifacts/raw.json objectKey OPENAI_API_KEY private-demo-token bearer token",
                0,
                JobDispatchEventStatus.DISPATCHED,
                0,
                NOW.minusSeconds(290),
                List.of(),
                new JobUsageSummaryVo(4, 0, 4000, new BigDecimal("0.01200000"), 1200, 800, new BigDecimal("45.0"), 1400),
                new JobCacheSummaryVo(1, 8, 2),
                List.of(),
                includeQualityEvaluation ? qualityEvaluation() : null,
                null,
                null
        );
    }

    private static QualityEvaluationVo qualityEvaluation() {
        return new QualityEvaluationVo(
                "quality-portal",
                "job-portal",
                "zh-CN",
                92,
                "GOOD",
                92,
                91,
                90,
                93,
                List.of("No blocking issues."),
                List.of("Review terminology before publication."),
                QualityEvaluationStatus.SUCCEEDED,
                null,
                NOW.minusSeconds(40)
        );
    }

    private static DemoReviewerWorkspaceVo reviewerWorkspace(String status) {
        return new DemoReviewerWorkspaceVo(
                "job-portal",
                "video-portal",
                NOW,
                status,
                "READY".equals(status) ? "REVIEW_PACKAGE_READY" : "REVIEW_PACKAGE_BLOCKED",
                "READY".equals(status) ? "Download reviewer workspace." : "Resolve reviewer blockers.",
                NOW.minusSeconds(30),
                "zh-CN",
                "tears-showcase",
                List.of(new DemoReviewerWorkspaceSectionVo("RUN", "Run summary", status, List.of("Safe reviewer facts."))),
                List.of(new DemoReviewerWorkspaceCheckVo("JOB_COMPLETED", "Job completed", status, "Completion check.", "Review job state.", true)),
                List.of(new DemoReviewerWorkspaceLinkVo("package", "Demo reviewer workspace", "/api/jobs/job-portal/demo-reviewer-workspace/download", "application/zip", "Reviewer package.")),
                List.of("manifest.json", "reviewer-workspace.md", "README.md"),
                List.of("Reviewer workspace excludes raw content.")
        );
    }

    private static DemoAcceptanceGateVo acceptance(String status) {
        return new DemoAcceptanceGateVo(
                "job-portal",
                "video-portal",
                NOW,
                status,
                "READY".equals(status) ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.PROCESSING,
                "zh-CN",
                "tears-showcase",
                "Portal run",
                "Safe summary only.",
                "READY".equals(status) ? "Present this run." : "Fix blockers before presenting.",
                List.of(new DemoAcceptanceGateCheckVo("JOB_COMPLETED", "Job completed", "READY".equals(status) ? "PASS" : "FAIL", "Completion check.", true)),
                List.of(new DemoAcceptanceGateEvidenceVo("SUBTITLE_OUTPUT_COUNT", "Subtitle outputs", "4", "READY")),
                List.of(new DemoAcceptanceGateLinkVo("ACCEPTANCE_GATE_JSON", "Acceptance gate", "/api/jobs/job-portal/demo-acceptance-gate")),
                List.of("Acceptance gate is metadata-only.")
        );
    }

    private static DemoCompletionCertificateVo certificate(String status) {
        return new DemoCompletionCertificateVo(
                "job-portal",
                "video-portal",
                NOW,
                status,
                "READY".equals(status) ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.PROCESSING,
                "zh-CN",
                "tears-showcase",
                "Completion certificate",
                "Safe completion summary.",
                "READY".equals(status) ? "Keep certificate with portal package." : "Resolve certificate blockers.",
                "job-baseline",
                "job-portal",
                "job-portal",
                List.of(new DemoCompletionCertificateCheckVo("DELIVERY", "Delivery", "READY".equals(status) ? "PASS" : "FAIL", "Delivery check.", !"READY".equals(status))),
                List.of(new DemoCompletionCertificateSectionVo("DELIVERY", "Delivery", "READY".equals(status) ? "READY" : "BLOCKED", List.of("Safe delivery facts."))),
                List.of(new DemoCompletionCertificateLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-portal/demo-run-package/download")),
                List.of("Completion certificate excludes media bytes.")
        );
    }

    private static DeliveryManifestVo manifest(boolean handoffReady) {
        return new DeliveryManifestVo(
                "job-portal",
                "video-portal",
                "zh-CN",
                "HIGH_CONTRAST",
                1,
                "glossary-hash",
                "BALANCED",
                "tears-showcase",
                handoffReady ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.PROCESSING,
                NOW,
                handoffReady,
                handoffReady ? 3 : 0,
                handoffReady,
                7,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static DemoRunSnapshotVo snapshot(String status) {
        return new DemoRunSnapshotVo(
                "job-portal",
                "video-portal",
                "zh-CN",
                "tears-showcase",
                NOW,
                status,
                "Static portal source",
                "Safe snapshot summary.",
                List.of(new DemoRunSnapshotSectionVo("SUMMARY", "Snapshot summary", status, "index.html", "Safe snapshot section.")),
                List.of("index.html", "manifest.json", "README.md"),
                List.of(new DemoRunSnapshotLinkVo("SNAPSHOT_ZIP", "Demo snapshot", "/api/jobs/job-portal/demo-run-snapshot/download")),
                List.of("No media bytes."),
                "safe snapshot markdown"
        );
    }

    private static DemoShareSheetVo shareSheet(String status) {
        return new DemoShareSheetVo(
                "job-portal",
                "video-portal",
                NOW,
                status,
                "Demo share sheet",
                "Safe share summary.",
                List.of("Completed localization evidence is ready."),
                "Share the handoff portal with reviewers.",
                List.of(new DemoShareSheetLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-portal/demo-run-package/download")),
                "safe share markdown"
        );
    }

    private static DemoRunMonitorVo monitor(String status) {
        return new DemoRunMonitorVo(
                "job-portal",
                "video-portal",
                "READY".equals(status) ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.PROCESSING,
                JobDispatchEventStatus.DISPATCHED,
                NOW,
                120_000L,
                LocalizationJobStage.COMPLETED,
                10,
                10,
                0,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                12_000L,
                status,
                "Safe monitor summary.",
                "Use portal package for handoff.",
                List.of(),
                List.of(new DemoRunMonitorLinkVo("MONITOR_JSON", "Demo run monitor", "/api/jobs/job-portal/demo-run-monitor")),
                "safe monitor markdown"
        );
    }

    private static OpenAiSmokeProofVo openAiProof(String status) {
        return new OpenAiSmokeProofVo(
                "job-portal",
                "video-portal",
                "zh-CN",
                status,
                "READY".equals(status) ? "OPENAI_SMOKE_PROVEN" : "OPENAI_SMOKE_NEEDS_REVIEW",
                "READY".equals(status) ? "Use OpenAI proof." : "Review OpenAI proof gaps.",
                NOW.minusSeconds(30),
                List.of(new OpenAiSmokeProofCheckVo("OpenAI transcription call", "READY".equals(status) ? "READY" : "ATTENTION", "Transcription check.", "Review provider evidence.")),
                List.of(new OpenAiSmokeProofCheckVo("Quality evaluation", "READY".equals(status) ? "READY" : "ATTENTION", "Quality check.", "Review quality evidence.")),
                List.of(),
                List.of(),
                List.of(new OpenAiSmokeProofLinkVo("OpenAI smoke proof", "/api/jobs/job-portal/openai-smoke-proof", "application/json", "Safe OpenAI proof.")),
                List.of("OpenAI proof excludes provider payloads.")
        );
    }

    private static Map<String, String> zipEntries(InputStream inputStream) throws IOException {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            java.util.LinkedHashMap<String, String> entries = new java.util.LinkedHashMap<>();
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                zipInputStream.transferTo(outputStream);
                entries.put(entry.getName(), outputStream.toString(StandardCharsets.UTF_8));
            }
            return entries;
        }
    }

    private record StaticLocalizationJobQueryService(LocalizationJobVo job) implements LocalizationJobQueryService {
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

    private record StaticReviewerWorkspaceService(DemoReviewerWorkspaceVo workspace) implements DemoReviewerWorkspaceService {
        @Override
        public DemoReviewerWorkspaceVo getWorkspace(String jobId) {
            return workspace;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "safe reviewer workspace";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredDemoReviewerWorkspacePackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticAcceptanceGateService(DemoAcceptanceGateVo gate) implements DemoAcceptanceGateService {
        @Override
        public DemoAcceptanceGateVo buildGate(String jobId) {
            return gate;
        }
    }

    private record StaticCompletionCertificateService(DemoCompletionCertificateVo certificate) implements DemoCompletionCertificateService {
        @Override
        public DemoCompletionCertificateVo buildCertificate(String jobId) {
            return certificate;
        }
    }

    private record StaticDeliveryManifestService(DeliveryManifestVo manifest) implements DeliveryManifestService {
        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return manifest;
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            return "safe manifest";
        }
    }

    private record StaticRunSnapshotService(DemoRunSnapshotVo snapshot) implements DemoRunSnapshotService {
        @Override
        public DemoRunSnapshotVo buildSnapshot(String jobId) {
            return snapshot;
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredDemoRunSnapshotPackageBo openSnapshotPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticShareSheetService(DemoShareSheetVo shareSheet) implements DemoShareSheetService {
        @Override
        public DemoShareSheetVo buildShareSheet(String jobId) {
            return shareSheet;
        }

        @Override
        public String buildMarkdownShareSheet(String jobId) {
            return "safe share sheet";
        }
    }

    private record StaticRunMonitorService(DemoRunMonitorVo monitor) implements DemoRunMonitorService {
        @Override
        public DemoRunMonitorVo buildMonitor(String jobId) {
            return monitor;
        }

        @Override
        public String buildMarkdownMonitor(String jobId) {
            return "safe monitor";
        }
    }

    private record StaticOpenAiSmokeProofService(OpenAiSmokeProofVo proof) implements OpenAiSmokeProofService {
        @Override
        public OpenAiSmokeProofVo getProof(String jobId) {
            return proof;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "safe openai proof";
        }
    }
}
