package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.StoredDemoHandoffPortalPackageBo;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
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
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageArtifactVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageCheckVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageLinkVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofCheckVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofLinkVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
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
import java.util.Optional;
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
                openAiProof("READY"),
                deliveryPackage("READY")
        );

        DemoHandoffPortalVo portal = service.getPortal("job-portal");

        assertThat(portal.jobId()).isEqualTo("job-portal");
        assertThat(portal.videoId()).isEqualTo("video-portal");
        assertThat(portal.generatedAt()).isEqualTo(NOW);
        assertThat(portal.overallStatus()).isEqualTo("READY");
        assertThat(portal.phase()).isEqualTo("HANDOFF_PORTAL_READY");
        assertThat(portal.headline()).contains("ready");
        assertThat(portal.checks()).extracting("status").containsOnly("READY");
        assertThat(portal.checks()).extracting("key")
                .contains("NARRATION_DELIVERY_PACKAGE", "CUSTOM_NARRATION_RENDER_HANDOFF", "FINAL_PROOF_BUNDLE");
        assertThat(portal.sections()).extracting("title")
                .contains("Reviewer workspace", "Offline portal", "Narration audio mix", "Narration delivery", "Custom narration render", "Final proof bundle", "Presentation evidence", "Safe packages");
        assertThat(portal.sections())
                .filteredOn(section -> section.key().equals("NARRATION_AUDIO_MIX"))
                .singleElement()
                .satisfies(section -> assertThat(section.facts())
                        .contains(
                                "Audio layout: TIMED_AUDIO_BED",
                                "Time aligned: true",
                                "Video mix mode: DUCKED_ORIGINAL_AUDIO",
                                "Ducking volume: 0.125",
                                "Narration volume: 1.750",
                                "Fade duration ms: 400",
                                "Mix settings source: SAVED"
                        ));
        assertThat(portal.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-portal/demo-handoff-portal/download",
                        "/api/jobs/job-portal/demo-reviewer-workspace/download",
                        "/api/jobs/job-portal/subtitle-review-evidence/download",
                        "/api/jobs/job-portal/narration-evidence/download",
                        "/api/jobs/job-portal/narration-recovery-handoff/download",
                        "/api/jobs/job-portal/narration-delivery-package",
                        "/api/jobs/job-portal/narration-delivery-package/markdown/download",
                        "/api/jobs/job-portal/narration-delivery-package/download",
                        "/api/jobs/job-portal/narration-workspace/generate-video",
                        "/api/jobs/job-portal/demo-run-snapshot/download",
                        "/api/jobs/job-portal/demo-run-package/download",
                        "/api/jobs/job-portal/demo-evidence-closure",
                        "/api/jobs/job-portal/demo-evidence-closure/markdown/download",
                        "/api/jobs/job-portal/demo-evidence-closure/download",
                        "/api/jobs/job-portal/openai-smoke-proof/markdown/download",
                        "/api/jobs/job-portal/ai-audit-package/download"
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
                "run-monitor.json",
                "narration-delivery-package.json",
                "narration-delivery-package.md",
                "final-proof-bundle.json",
                "final-proof-bundle.md"
        );
        assertThat(entries.get("index.html"))
                .contains("<!doctype html>")
                .contains("LinguaFrame Demo Handoff Portal")
                .contains("HANDOFF_PORTAL_READY")
                .contains("DUCKED_ORIGINAL_AUDIO")
                .contains("Narration volume: 1.750")
                .contains("Fade duration ms: 400")
                .contains("Narration delivery package")
                .contains("/api/jobs/job-portal/narration-delivery-package/download")
                .contains("Final proof bundle")
                .contains("/api/jobs/job-portal/demo-evidence-closure/download")
                .contains("/api/jobs/job-portal/openai-smoke-proof/markdown/download")
                .contains("/api/jobs/job-portal/ai-audit-package/download");
        assertThat(entries.get("manifest.json"))
                .contains("final-proof-bundle.json")
                .contains("final-proof-bundle.md");
        assertThat(entries.get("final-proof-bundle.json"))
                .contains("\"jobId\":\"job-portal\"")
                .contains("\"source\":\"final-proof-bundle\"")
                .contains("\"evidenceClosureHref\":\"/api/jobs/job-portal/demo-evidence-closure\"");
        assertThat(entries.get("final-proof-bundle.md"))
                .contains("Final proof bundle")
                .contains("/api/jobs/job-portal/demo-evidence-closure/download")
                .contains("/api/jobs/job-portal/ai-audit-package/download")
                .contains("actual-only");
        assertThat(entries.keySet())
                .doesNotContain("narration-delivery-package.zip", "demo-evidence-closure.zip", "ai-audit-package.zip");
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
                openAiProof("ATTENTION"),
                deliveryPackage("READY")
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
                openAiProof("ATTENTION"),
                deliveryPackage("BLOCKED")
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
                openAiProof("READY"),
                deliveryPackage("READY")
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
                        "/api/jobs/job-portal/narration-recovery-handoff/download",
                        "/api/jobs/job-portal/narration-delivery-package/download",
                        "/api/jobs/job-portal/narration-workspace/generate-video"
                );
        assertThat(portal.packageEntries())
                .contains(
                        "Linked safe route: /api/jobs/job-portal/subtitle-review-evidence/download",
                        "Linked safe route: /api/jobs/job-portal/narration-evidence/download",
                        "Linked safe route: /api/jobs/job-portal/narration-recovery-handoff/download",
                        "Linked safe route: /api/jobs/job-portal/narration-delivery-package/download",
                        "Linked safe route: /api/jobs/job-portal/demo-evidence-closure/download",
                        "Linked safe route: /api/jobs/job-portal/openai-smoke-proof/markdown/download",
                        "Linked safe route: /api/jobs/job-portal/ai-audit-package/download",
                        "Linked safe route: /api/jobs/job-portal/narration-workspace/generate-video"
                );
        assertThat(entries.get("narration-delivery-package.json"))
                .contains("\"jobId\":\"job-portal\"")
                .contains("\"status\":\"READY\"");
        assertThat(entries.get("narration-delivery-package.md"))
                .contains("Narration delivery package")
                .contains("metadata-only");
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
            OpenAiSmokeProofVo openAiProof,
            NarrationDeliveryPackageVo deliveryPackage
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
                new StaticNarrationDeliveryPackageService(deliveryPackage),
                new StaticNarrationMixSettingsRepository(new NarrationMixSettingsRecord(
                        "job-portal",
                        new BigDecimal("0.125"),
                        new BigDecimal("1.750"),
                        400,
                        NOW.minusSeconds(60)
                )),
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
                List.of(
                        new DemoReviewerWorkspaceLinkVo("package", "Demo reviewer workspace", "/api/jobs/job-portal/demo-reviewer-workspace/download", "application/zip", "Reviewer package."),
                        new DemoReviewerWorkspaceLinkVo("NARRATION_DELIVERY_PACKAGE_ZIP", "Narration delivery package ZIP", "/api/jobs/job-portal/narration-delivery-package/download", "application/zip", "Narration delivery package.")
                ),
                List.of("manifest.json", "reviewer-workspace.md", "README.md", "narration-delivery-package.json", "narration-delivery-package.md"),
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
                List.of(),
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

    private static NarrationDeliveryPackageVo deliveryPackage(String status) {
        boolean ready = "READY".equals(status);
        return new NarrationDeliveryPackageVo(
                "job-portal",
                NOW,
                status,
                ready ? "NARRATION_DELIVERY_READY" : "NARRATION_DELIVERY_BLOCKED",
                ready ? "Download the narration delivery package." : "Resolve narration delivery blockers.",
                ready,
                ready,
                ready ? 0 : 2,
                ready ? "READY" : "ATTENTION",
                "READY",
                ready ? "READY" : "ATTENTION",
                ready ? "READY" : "ATTENTION",
                ready ? "READY" : "ATTENTION",
                ready ? "READY" : "BLOCKED",
                ready ? List.of(new NarrationDeliveryPackageArtifactVo("audio-artifact", "NARRATION_AUDIO", "narration-audio.mp3", "audio/mpeg", 1024, false, "/api/jobs/job-portal/artifacts/audio-artifact/download")) : List.of(),
                List.of(new NarrationDeliveryPackageCheckVo("NARRATION_PLAYBACK_RESOLUTION", "Playback resolution", ready ? "READY" : "BLOCKED", "Playback resolution status.", "Review playback resolution.", true)),
                List.of(
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_JSON", "Narration delivery package JSON", "/api/jobs/job-portal/narration-delivery-package", "application/json", "Delivery metadata."),
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_MARKDOWN", "Narration delivery package Markdown", "/api/jobs/job-portal/narration-delivery-package/markdown/download", "text/markdown", "Delivery Markdown."),
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_ZIP", "Narration delivery package ZIP", "/api/jobs/job-portal/narration-delivery-package/download", "application/zip", "Delivery ZIP.")
                ),
                List.of("manifest.json", "narration-delivery-package.json", "narration-delivery-package.md", "README.md"),
                List.of("Narration delivery package is metadata-only.")
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

    private record StaticNarrationDeliveryPackageService(
            NarrationDeliveryPackageVo deliveryPackage
    ) implements NarrationDeliveryPackageService {
        @Override
        public NarrationDeliveryPackageVo getSummary(String jobId) {
            return deliveryPackage;
        }

        @Override
        public NarrationDeliveryPackageVo getPackage(String jobId) {
            return deliveryPackage;
        }

        @Override
        public String renderMarkdown(String jobId) {
            return "# Narration delivery package\n\n- Status: " + deliveryPackage.status() + "\n- Safety: metadata-only\n";
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredNarrationDeliveryPackageBo openPackage(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticNarrationMixSettingsRepository(NarrationMixSettingsRecord settings)
            implements NarrationMixSettingsRepository {

        @Override
        public Optional<NarrationMixSettingsRecord> findByJobId(String jobId) {
            return Optional.ofNullable(settings)
                    .filter(record -> record.jobId().equals(jobId));
        }

        @Override
        public NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
    }
}
