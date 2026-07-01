package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
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
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
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
import com.linguaframe.job.service.impl.DemoReviewerWorkspaceServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DemoReviewerWorkspaceServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-29T13:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void buildsReadyReviewerWorkspaceFromCompletedRunEvidence() {
        DemoReviewerWorkspaceService service = service(
                job(LocalizationJobStatus.COMPLETED, true),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                openAiProof("READY"),
                deliveryPackage("READY")
        );

        DemoReviewerWorkspaceVo workspace = service.getWorkspace("job-reviewer");

        assertThat(workspace.jobId()).isEqualTo("job-reviewer");
        assertThat(workspace.videoId()).isEqualTo("video-reviewer");
        assertThat(workspace.generatedAt()).isEqualTo(NOW);
        assertThat(workspace.overallStatus()).isEqualTo("READY");
        assertThat(workspace.phase()).isEqualTo("REVIEW_PACKAGE_READY");
        assertThat(workspace.checks()).extracting("status").containsOnly("READY");
        assertThat(workspace.checks()).extracting("key")
                .contains("NARRATION_DELIVERY_PACKAGE", "CUSTOM_NARRATION_RENDER_HANDOFF", "FINAL_PROOF_BUNDLE");
        assertThat(workspace.sections()).extracting("title")
                .contains("Run summary", "Delivery", "OpenAI proof", "Narration delivery", "Custom narration render", "Final proof bundle", "Packages");
        assertThat(workspace.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-reviewer",
                        "/api/jobs/job-reviewer/demo-reviewer-workspace/markdown/download",
                        "/api/jobs/job-reviewer/demo-reviewer-workspace/download",
                        "/api/jobs/job-reviewer/demo-run-package/download",
                        "/api/jobs/job-reviewer/ai-audit-package/download",
                        "/api/jobs/job-reviewer/demo-evidence-closure",
                        "/api/jobs/job-reviewer/demo-evidence-closure/markdown/download",
                        "/api/jobs/job-reviewer/demo-evidence-closure/download",
                        "/api/jobs/job-reviewer/openai-smoke-proof/markdown/download",
                        "/api/jobs/job-reviewer/custom-narration-render/markdown/download",
                        "/api/jobs/job-reviewer/narration-delivery-package",
                        "/api/jobs/job-reviewer/narration-delivery-package/markdown/download",
                        "/api/jobs/job-reviewer/narration-delivery-package/download"
                );
        assertThat(workspace.packageEntries())
                .contains(
                        "narration-delivery-package.json",
                        "narration-delivery-package.md",
                        "final-proof-bundle.json",
                        "final-proof-bundle.md",
                        "Linked safe route: /api/jobs/job-reviewer/demo-evidence-closure/download",
                        "Linked safe route: /api/jobs/job-reviewer/openai-smoke-proof/markdown/download",
                        "Linked safe route: /api/jobs/job-reviewer/narration-delivery-package/download",
                        "Linked safe route: /api/jobs/job-reviewer/custom-narration-render/markdown/download"
                );
        assertThat(workspace.recommendedNextAction()).contains("share");
    }

    @Test
    void warnsWhenOptionalOpenAiProofIsMissingForDeterministicRun() {
        DemoReviewerWorkspaceService service = service(
                job(LocalizationJobStatus.COMPLETED, false),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                openAiProof("ATTENTION"),
                deliveryPackage("READY")
        );

        DemoReviewerWorkspaceVo workspace = service.getWorkspace("job-reviewer");

        assertThat(workspace.overallStatus()).isEqualTo("ATTENTION");
        assertThat(workspace.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("OPENAI_SMOKE_PROOF");
                    assertThat(check.status()).isEqualTo("ATTENTION");
                    assertThat(check.required()).isFalse();
                });
    }

    @Test
    void blocksWhenAcceptanceGateBlocksTheRun() {
        DemoReviewerWorkspaceService service = service(
                job(LocalizationJobStatus.PROCESSING, false),
                acceptance("BLOCKED"),
                certificate("BLOCKED"),
                manifest(false),
                openAiProof("ATTENTION"),
                deliveryPackage("BLOCKED")
        );

        DemoReviewerWorkspaceVo workspace = service.getWorkspace("job-reviewer");

        assertThat(workspace.overallStatus()).isEqualTo("BLOCKED");
        assertThat(workspace.phase()).isEqualTo("REVIEW_PACKAGE_BLOCKED");
        assertThat(workspace.checks())
                .anySatisfy(check -> {
                    assertThat(check.key()).isEqualTo("ACCEPTANCE_GATE");
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.required()).isTrue();
                });
    }

    @Test
    void rendersSafeMarkdownWithoutSecretsOrRawContent() {
        DemoReviewerWorkspaceService service = service(
                job(LocalizationJobStatus.COMPLETED, true),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                openAiProof("READY"),
                deliveryPackage("READY")
        );

        String markdown = service.renderMarkdown("job-reviewer");

        assertThat(markdown)
                .contains("# LinguaFrame Demo Reviewer Workspace")
                .contains("- Job: job-reviewer")
                .contains("- Overall status: READY")
                .contains("Demo run package")
                .contains("OpenAI smoke proof")
                .contains("Final proof bundle")
                .contains("/api/jobs/job-reviewer/demo-evidence-closure/download")
                .contains("/api/jobs/job-reviewer/ai-audit-package/download")
                .contains("Narration delivery package")
                .contains("/api/jobs/job-reviewer/narration-delivery-package/download");
        assertThat(markdown)
                .doesNotContain("sk-test")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("/Users/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    @Test
    void packageIncludesNarrationDeliverySummariesWithoutNestedZip() throws IOException {
        DemoReviewerWorkspaceService service = service(
                job(LocalizationJobStatus.COMPLETED, true),
                acceptance("READY"),
                certificate("READY"),
                manifest(true),
                openAiProof("READY"),
                deliveryPackage("READY")
        );

        Map<String, String> entries = zipEntries(service.openPackage("job-reviewer").inputStream());

        assertThat(entries.keySet())
                .contains(
                        "narration-delivery-package.json",
                        "narration-delivery-package.md",
                        "final-proof-bundle.json",
                        "final-proof-bundle.md"
                )
                .doesNotContain(
                        "narration-delivery-package.zip",
                        "demo-evidence-closure.zip",
                        "ai-audit-package.zip",
                        "openai-smoke-proof.md"
                );
        assertThat(entries.get("narration-delivery-package.json"))
                .contains("\"jobId\":\"job-reviewer\"")
                .contains("\"status\":\"READY\"");
        assertThat(entries.get("narration-delivery-package.md"))
                .contains("Narration delivery package")
                .contains("metadata-only");
        assertThat(entries.get("final-proof-bundle.json"))
                .contains("\"jobId\":\"job-reviewer\"")
                .contains("\"source\":\"final-proof-bundle\"")
                .contains("\"evidenceClosureHref\":\"/api/jobs/job-reviewer/demo-evidence-closure\"");
        assertThat(entries.get("final-proof-bundle.md"))
                .contains("Final proof bundle")
                .contains("/api/jobs/job-reviewer/demo-evidence-closure/download")
                .contains("/api/jobs/job-reviewer/ai-audit-package/download")
                .contains("actual-only");
    }

    private static DemoReviewerWorkspaceService service(
            LocalizationJobVo job,
            DemoAcceptanceGateVo acceptance,
            DemoCompletionCertificateVo certificate,
            DeliveryManifestVo manifest,
            OpenAiSmokeProofVo openAiProof,
            NarrationDeliveryPackageVo deliveryPackage
    ) {
        return new DemoReviewerWorkspaceServiceImpl(
                new StaticLocalizationJobQueryService(job),
                new StaticAcceptanceGateService(acceptance),
                new StaticCompletionCertificateService(certificate),
                new StaticDeliveryManifestService(manifest),
                new StaticOpenAiSmokeProofService(openAiProof),
                new StaticNarrationDeliveryPackageService(deliveryPackage),
                CLOCK
        );
    }

    private static LocalizationJobVo job(LocalizationJobStatus status, boolean includeQualityEvaluation) {
        return new LocalizationJobVo(
                "job-reviewer",
                "video-reviewer",
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
                "provider payload raw transcript text raw subtitle text sk-test /Users/example job-artifacts/raw.json OPENAI_API_KEY private-demo-token",
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
                "quality-reviewer",
                "job-reviewer",
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

    private static DemoAcceptanceGateVo acceptance(String status) {
        return new DemoAcceptanceGateVo(
                "job-reviewer",
                "video-reviewer",
                NOW,
                status,
                "READY".equals(status) ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.PROCESSING,
                "zh-CN",
                "tears-showcase",
                "Reviewer run",
                "Safe summary only.",
                "READY".equals(status) ? "Present this run." : "Fix blockers before presenting.",
                List.of(),
                List.of(new DemoAcceptanceGateCheckVo("JOB_COMPLETED", "Job completed", "READY".equals(status) ? "PASS" : "FAIL", "Completion check.", true)),
                List.of(new DemoAcceptanceGateEvidenceVo("SUBTITLE_OUTPUT_COUNT", "Subtitle outputs", "4", "READY")),
                List.of(new DemoAcceptanceGateLinkVo("ACCEPTANCE_GATE_JSON", "Acceptance gate", "/api/jobs/job-reviewer/demo-acceptance-gate")),
                List.of("Acceptance gate is metadata-only.")
        );
    }

    private static DemoCompletionCertificateVo certificate(String status) {
        return new DemoCompletionCertificateVo(
                "job-reviewer",
                "video-reviewer",
                NOW,
                status,
                "READY".equals(status) ? LocalizationJobStatus.COMPLETED : LocalizationJobStatus.PROCESSING,
                "zh-CN",
                "tears-showcase",
                "Completion certificate",
                "Safe completion summary.",
                "READY".equals(status) ? "Keep certificate with reviewer package." : "Resolve certificate blockers.",
                "job-baseline",
                "job-reviewer",
                "job-reviewer",
                List.of(new DemoCompletionCertificateCheckVo("DELIVERY", "Delivery", "READY".equals(status) ? "PASS" : "FAIL", "Delivery check.", !"READY".equals(status))),
                List.of(new DemoCompletionCertificateSectionVo("DELIVERY", "Delivery", "READY".equals(status) ? "READY" : "BLOCKED", List.of("Safe delivery facts."))),
                List.of(new DemoCompletionCertificateLinkVo("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/job-reviewer/demo-run-package/download")),
                List.of("Completion certificate excludes media bytes.")
        );
    }

    private static DeliveryManifestVo manifest(boolean handoffReady) {
        return new DeliveryManifestVo(
                "job-reviewer",
                "video-reviewer",
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

    private static OpenAiSmokeProofVo openAiProof(String status) {
        return new OpenAiSmokeProofVo(
                "job-reviewer",
                "video-reviewer",
                "zh-CN",
                status,
                "READY".equals(status) ? "OPENAI_SMOKE_PROVEN" : "OPENAI_SMOKE_NEEDS_REVIEW",
                "READY".equals(status) ? "Use OpenAI proof." : "Review OpenAI proof gaps.",
                NOW.minusSeconds(30),
                List.of(new OpenAiSmokeProofCheckVo("OpenAI transcription call", "READY".equals(status) ? "READY" : "ATTENTION", "Transcription check.", "Review provider evidence.")),
                List.of(new OpenAiSmokeProofCheckVo("Quality evaluation", "READY".equals(status) ? "READY" : "ATTENTION", "Quality check.", "Review quality evidence.")),
                List.of(),
                List.of(),
                List.of(new OpenAiSmokeProofLinkVo("OpenAI smoke proof", "/api/jobs/job-reviewer/openai-smoke-proof", "application/json", "Safe OpenAI proof.")),
                List.of("OpenAI proof excludes provider payloads.")
        );
    }

    private static NarrationDeliveryPackageVo deliveryPackage(String status) {
        boolean ready = "READY".equals(status);
        return new NarrationDeliveryPackageVo(
                "job-reviewer",
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
                ready ? List.of(new NarrationDeliveryPackageArtifactVo("audio-artifact", "NARRATION_AUDIO", "narration-audio.mp3", "audio/mpeg", 1024, false, "/api/jobs/job-reviewer/artifacts/audio-artifact/download")) : List.of(),
                List.of(new NarrationDeliveryPackageCheckVo("NARRATION_PLAYBACK_RESOLUTION", "Playback resolution", ready ? "READY" : "BLOCKED", "Playback resolution status.", "Review playback resolution.", true)),
                List.of(
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_JSON", "Narration delivery package JSON", "/api/jobs/job-reviewer/narration-delivery-package", "application/json", "Delivery metadata."),
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_MARKDOWN", "Narration delivery package Markdown", "/api/jobs/job-reviewer/narration-delivery-package/markdown/download", "text/markdown", "Delivery Markdown."),
                        new NarrationDeliveryPackageLinkVo("NARRATION_DELIVERY_PACKAGE_ZIP", "Narration delivery package ZIP", "/api/jobs/job-reviewer/narration-delivery-package/download", "application/zip", "Delivery ZIP.")
                ),
                List.of("manifest.json", "narration-delivery-package.json", "narration-delivery-package.md", "README.md"),
                List.of("Narration delivery package is metadata-only.")
        );
    }

    private static Map<String, String> zipEntries(InputStream inputStream) throws IOException {
        java.util.LinkedHashMap<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                zip.transferTo(output);
                entries.put(entry.getName(), output.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
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
}
