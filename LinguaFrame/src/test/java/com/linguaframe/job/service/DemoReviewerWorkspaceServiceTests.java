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
import com.linguaframe.job.domain.vo.OpenAiSmokeProofCheckVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofLinkVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoReviewerWorkspaceServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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
                openAiProof("READY")
        );

        DemoReviewerWorkspaceVo workspace = service.getWorkspace("job-reviewer");

        assertThat(workspace.jobId()).isEqualTo("job-reviewer");
        assertThat(workspace.videoId()).isEqualTo("video-reviewer");
        assertThat(workspace.generatedAt()).isEqualTo(NOW);
        assertThat(workspace.overallStatus()).isEqualTo("READY");
        assertThat(workspace.phase()).isEqualTo("REVIEW_PACKAGE_READY");
        assertThat(workspace.checks()).extracting("status").containsOnly("READY");
        assertThat(workspace.sections()).extracting("title")
                .contains("Run summary", "Delivery", "OpenAI proof", "Packages");
        assertThat(workspace.safeLinks()).extracting("href")
                .contains(
                        "/api/jobs/job-reviewer",
                        "/api/jobs/job-reviewer/demo-reviewer-workspace/markdown/download",
                        "/api/jobs/job-reviewer/demo-reviewer-workspace/download",
                        "/api/jobs/job-reviewer/demo-run-package/download",
                        "/api/jobs/job-reviewer/ai-audit-package/download"
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
                openAiProof("ATTENTION")
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
                openAiProof("ATTENTION")
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
                openAiProof("READY")
        );

        String markdown = service.renderMarkdown("job-reviewer");

        assertThat(markdown)
                .contains("# LinguaFrame Demo Reviewer Workspace")
                .contains("- Job: job-reviewer")
                .contains("- Overall status: READY")
                .contains("Demo run package")
                .contains("OpenAI smoke proof");
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

    private static DemoReviewerWorkspaceService service(
            LocalizationJobVo job,
            DemoAcceptanceGateVo acceptance,
            DemoCompletionCertificateVo certificate,
            DeliveryManifestVo manifest,
            OpenAiSmokeProofVo openAiProof
    ) {
        return new DemoReviewerWorkspaceServiceImpl(
                new StaticLocalizationJobQueryService(job),
                new StaticAcceptanceGateService(acceptance),
                new StaticCompletionCertificateService(certificate),
                new StaticDeliveryManifestService(manifest),
                new StaticOpenAiSmokeProofService(openAiProof),
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
}
