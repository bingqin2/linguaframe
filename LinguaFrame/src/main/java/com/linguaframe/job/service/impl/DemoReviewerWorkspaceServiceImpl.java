package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.bo.StoredDemoReviewerWorkspacePackageBo;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceCheckVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceLinkVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceSectionVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoCompletionCertificateService;
import com.linguaframe.job.service.DemoReviewerWorkspaceService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationDeliveryPackageService;
import com.linguaframe.job.service.OpenAiSmokeProofService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DemoReviewerWorkspaceServiceImpl implements DemoReviewerWorkspaceService {

    private final LocalizationJobQueryService queryService;
    private final DemoAcceptanceGateService acceptanceGateService;
    private final DemoCompletionCertificateService completionCertificateService;
    private final DeliveryManifestService deliveryManifestService;
    private final OpenAiSmokeProofService openAiSmokeProofService;
    private final NarrationDeliveryPackageService narrationDeliveryPackageService;
    private final Clock clock;

    @Autowired
    public DemoReviewerWorkspaceServiceImpl(
            LocalizationJobQueryService queryService,
            DemoAcceptanceGateService acceptanceGateService,
            DemoCompletionCertificateService completionCertificateService,
            DeliveryManifestService deliveryManifestService,
            OpenAiSmokeProofService openAiSmokeProofService,
            NarrationDeliveryPackageService narrationDeliveryPackageService
    ) {
        this(queryService, acceptanceGateService, completionCertificateService, deliveryManifestService,
                openAiSmokeProofService, narrationDeliveryPackageService, Clock.systemUTC());
    }

    public DemoReviewerWorkspaceServiceImpl(
            LocalizationJobQueryService queryService,
            DemoAcceptanceGateService acceptanceGateService,
            DemoCompletionCertificateService completionCertificateService,
            DeliveryManifestService deliveryManifestService,
            OpenAiSmokeProofService openAiSmokeProofService,
            NarrationDeliveryPackageService narrationDeliveryPackageService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.acceptanceGateService = acceptanceGateService;
        this.completionCertificateService = completionCertificateService;
        this.deliveryManifestService = deliveryManifestService;
        this.openAiSmokeProofService = openAiSmokeProofService;
        this.narrationDeliveryPackageService = narrationDeliveryPackageService;
        this.clock = clock;
    }

    @Override
    public DemoReviewerWorkspaceVo getWorkspace(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        DemoAcceptanceGateVo acceptance = acceptanceGateService.buildGate(jobId);
        DemoCompletionCertificateVo certificate = completionCertificateService.buildCertificate(jobId);
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
        OpenAiSmokeProofVo openAiProof = openAiSmokeProofService.getProof(jobId);
        NarrationDeliveryPackageVo narrationDelivery = narrationDeliveryPackageService.getSummary(jobId);
        List<DemoReviewerWorkspaceCheckVo> checks = checks(job, acceptance, certificate, manifest, openAiProof, narrationDelivery);
        String overallStatus = overallStatus(checks);
        return new DemoReviewerWorkspaceVo(
                job.jobId(),
                job.videoId(),
                Instant.now(clock),
                overallStatus,
                phase(overallStatus),
                recommendedNextAction(overallStatus),
                job.completedAt(),
                job.targetLanguage(),
                job.demoProfileId(),
                sections(job, acceptance, certificate, manifest, openAiProof, narrationDelivery),
                checks,
                safeLinks(job.jobId(), narrationDelivery),
                packageEntries(job.jobId()),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        DemoReviewerWorkspaceVo workspace = getWorkspace(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Demo Reviewer Workspace");
        lines.add("");
        lines.add("## Summary");
        lines.add("- Job: " + value(workspace.jobId()));
        lines.add("- Video: " + value(workspace.videoId()));
        lines.add("- Overall status: " + value(workspace.overallStatus()));
        lines.add("- Phase: " + value(workspace.phase()));
        lines.add("- Target language: " + value(workspace.targetLanguage()));
        lines.add("- Demo profile: " + value(workspace.demoProfileId()));
        lines.add("- Completed at: " + value(workspace.completedAt()));
        lines.add("- Recommended next action: " + value(workspace.recommendedNextAction()));
        lines.add("");
        lines.add("## Reviewer Checks");
        for (DemoReviewerWorkspaceCheckVo check : workspace.checks()) {
            lines.add("- " + check.label() + ": " + check.status()
                    + " - " + value(check.detail())
                    + " Next: " + value(check.nextAction()));
        }
        lines.add("");
        lines.add("## Sections");
        for (DemoReviewerWorkspaceSectionVo section : workspace.sections()) {
            lines.add("### " + section.title());
            lines.add("- Status: " + value(section.status()));
            for (String fact : section.facts()) {
                lines.add("- " + value(fact));
            }
            lines.add("");
        }
        lines.add("## Package Inventory");
        for (String entry : workspace.packageEntries()) {
            lines.add("- " + entry);
        }
        lines.add("");
        lines.add("## Safe Links");
        for (DemoReviewerWorkspaceLinkVo link : workspace.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : workspace.safetyNotes()) {
            lines.add("- " + note);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    @Override
    public StoredDemoReviewerWorkspacePackageBo openPackage(String jobId) {
        DemoReviewerWorkspaceVo workspace = getWorkspace(jobId);
        String markdown = renderMarkdown(jobId);
        byte[] content = zipBytes(workspace, markdown);
        return new StoredDemoReviewerWorkspacePackageBo(
                "linguaframe-job-" + workspace.jobId() + "-demo-reviewer-workspace.zip",
                "application/zip",
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private List<DemoReviewerWorkspaceCheckVo> checks(
            LocalizationJobVo job,
            DemoAcceptanceGateVo acceptance,
            DemoCompletionCertificateVo certificate,
            DeliveryManifestVo manifest,
            OpenAiSmokeProofVo openAiProof,
            NarrationDeliveryPackageVo narrationDelivery
    ) {
        List<DemoReviewerWorkspaceCheckVo> checks = new ArrayList<>();
        checks.add(job.status() == LocalizationJobStatus.COMPLETED
                ? ready("JOB_COMPLETED", "Job completed", "Job reached COMPLETED.", "Keep the completed job id with reviewer evidence.", true)
                : blocked("JOB_COMPLETED", "Job completed", "Job status is " + job.status() + ".", "Wait for completion or inspect diagnostics.", true));
        checks.add(statusCheck("ACCEPTANCE_GATE", "Acceptance gate", acceptance.gateStatus(), true,
                "Acceptance gate status is " + acceptance.gateStatus() + ".",
                acceptance.recommendedNextAction()));
        checks.add(statusCheck("COMPLETION_CERTIFICATE", "Completion certificate", certificate.certificateStatus(), true,
                "Completion certificate status is " + certificate.certificateStatus() + ".",
                certificate.recommendedNextAction()));
        checks.add(manifest.handoffReady()
                ? ready("DELIVERY_HANDOFF", "Delivery handoff", "Delivery manifest is handoff-ready.", "Use handoff package when reviewed outputs are needed.", true)
                : blocked("DELIVERY_HANDOFF", "Delivery handoff", "Delivery manifest is not handoff-ready.", "Review delivery manifest before sharing.", true));
        checks.add(ready("SAFE_EVIDENCE_LINKS", "Safe evidence links", "Diagnostics, evidence, reviewer workspace, and package links are available.", "Use links instead of copying raw artifacts.", true));
        checks.add(statusCheck("OPENAI_SMOKE_PROOF", "OpenAI smoke proof", openAiProof.overallStatus(), false,
                "OpenAI smoke proof status is " + openAiProof.overallStatus() + ".",
                openAiProof.recommendedNextAction()));
        checks.add(statusCheck("FINAL_PROOF_BUNDLE", "Final proof bundle",
                finalProofStatus(acceptance.gateStatus(), certificate.certificateStatus(), openAiProof.overallStatus()),
                false,
                "Final proof bundle links completion, acceptance, OpenAI proof, AI audit, and evidence closure.",
                "Use actual-only evidence closure when no pre-upload baseline JSON is available."));
        checks.add(statusCheck("NARRATION_DELIVERY_PACKAGE", "Narration delivery package", narrationDelivery.status(), false,
                "Narration delivery package status is " + narrationDelivery.status()
                        + "; audioReady=" + narrationDelivery.audioReady()
                        + "; videoReady=" + narrationDelivery.videoReady() + ".",
                narrationDelivery.recommendedNextAction()));
        checks.add(job.qualityEvaluation() == null
                ? attention("QUALITY_EVALUATION", "Quality evaluation", "No quality evaluation is attached to this job.", "Mention deterministic or unevaluated run if presenting.", false)
                : ready("QUALITY_EVALUATION", "Quality evaluation", "Quality evaluation is attached to this job.", "Keep quality evidence with reviewer package.", false));
        return List.copyOf(checks);
    }

    private List<DemoReviewerWorkspaceSectionVo> sections(
            LocalizationJobVo job,
            DemoAcceptanceGateVo acceptance,
            DemoCompletionCertificateVo certificate,
            DeliveryManifestVo manifest,
            OpenAiSmokeProofVo openAiProof,
            NarrationDeliveryPackageVo narrationDelivery
    ) {
        return List.of(
                section("RUN_SUMMARY", "Run summary", job.status().name(), List.of(
                        "Job status: " + job.status(),
                        "Target language: " + value(job.targetLanguage()),
                        "Demo profile: " + value(job.demoProfileId()),
                        "Model calls: " + job.usageSummary().modelCallCount(),
                        "Estimated cost USD: " + value(job.usageSummary().estimatedCostUsd())
                )),
                section("DELIVERY", "Delivery", manifest.handoffReady() ? "READY" : "BLOCKED", List.of(
                        "Generated artifact count: " + manifest.generatedArtifactCount(),
                        "Reviewed subtitle artifacts: " + manifest.reviewedSubtitleArtifactCount(),
                        "Reviewed burned video available: " + manifest.reviewedBurnedVideoAvailable()
                )),
                section("OPENAI_PROOF", "OpenAI proof", openAiProof.overallStatus(), List.of(
                        "OpenAI smoke proof: " + openAiProof.overallStatus(),
                        "OpenAI proof phase: " + value(openAiProof.phase()),
                        "OpenAI model-call rows: " + openAiProof.modelCalls().size()
                )),
                section("NARRATION_DELIVERY", "Narration delivery", narrationDelivery.status(), List.of(
                        "Narration delivery package: " + narrationDelivery.status(),
                        "Narration audio ready: " + narrationDelivery.audioReady(),
                        "Narrated video ready: " + narrationDelivery.videoReady(),
                        "Delivery package entries: " + narrationDelivery.packageEntries().size(),
                        "Delivery next action: " + value(narrationDelivery.recommendedNextAction())
                )),
                section("FINAL_PROOF_BUNDLE", "Final proof bundle",
                        finalProofStatus(acceptance.gateStatus(), certificate.certificateStatus(), openAiProof.overallStatus()),
                        List.of(
                                "Acceptance gate: " + acceptance.gateStatus(),
                                "Completion certificate: " + certificate.certificateStatus(),
                                "OpenAI smoke proof: " + openAiProof.overallStatus(),
                                "AI audit package route is available.",
                                "Evidence closure route is available.",
                                "Evidence closure baseline mode: actual-only when no pre-upload baseline JSON is supplied."
                        )),
                section("PACKAGES", "Packages", certificate.certificateStatus(), List.of(
                        "Acceptance gate: " + acceptance.gateStatus(),
                        "Completion certificate: " + certificate.certificateStatus(),
                        "Demo run package link is available.",
                        "AI audit package link is available.",
                        "Reviewer workspace ZIP link is available."
                ))
        );
    }

    private static DemoReviewerWorkspaceCheckVo statusCheck(
            String key,
            String label,
            String sourceStatus,
            boolean required,
            String detail,
            String nextAction
    ) {
        if (isReadyStatus(sourceStatus)) {
            return ready(key, label, detail, nextAction, required);
        }
        if (isBlockedStatus(sourceStatus) && required) {
            return blocked(key, label, detail, nextAction, true);
        }
        return attention(key, label, detail, nextAction, required);
    }

    private static String overallStatus(List<DemoReviewerWorkspaceCheckVo> checks) {
        if (checks.stream().anyMatch(check -> check.required() && "BLOCKED".equals(check.status()))) {
            return "BLOCKED";
        }
        if (checks.stream().anyMatch(check -> "ATTENTION".equals(check.status()) || "BLOCKED".equals(check.status()))) {
            return "ATTENTION";
        }
        return "READY";
    }

    private static String phase(String status) {
        return switch (status) {
            case "READY" -> "REVIEW_PACKAGE_READY";
            case "ATTENTION" -> "REVIEW_PACKAGE_NEEDS_REVIEW";
            default -> "REVIEW_PACKAGE_BLOCKED";
        };
    }

    private static String recommendedNextAction(String status) {
        return switch (status) {
            case "READY" -> "Download the reviewer workspace ZIP and share it with the demo evidence links.";
            case "ATTENTION" -> "Review optional evidence gaps before sharing the reviewer workspace.";
            default -> "Resolve blocked reviewer checks before sharing this run.";
        };
    }

    private static List<DemoReviewerWorkspaceLinkVo> safeLinks(String jobId, NarrationDeliveryPackageVo narrationDelivery) {
        List<DemoReviewerWorkspaceLinkVo> links = new ArrayList<>(List.of(
                link("JOB_DETAIL", "Job detail", "/api/jobs/" + jobId, "application/json", "Safe job detail."),
                link("REVIEWER_MARKDOWN", "Reviewer workspace Markdown", "/api/jobs/" + jobId + "/demo-reviewer-workspace/markdown/download", "text/markdown", "Reviewer summary as Markdown."),
                link("REVIEWER_ZIP", "Reviewer workspace ZIP", "/api/jobs/" + jobId + "/demo-reviewer-workspace/download", "application/zip", "Reviewer metadata package."),
                link("DIAGNOSTICS", "Diagnostics", "/api/jobs/" + jobId + "/diagnostics/download", "application/json", "Safe diagnostics report."),
                link("EVIDENCE_MARKDOWN", "Evidence Markdown", "/api/jobs/" + jobId + "/evidence/markdown/download", "text/markdown", "Backend evidence report."),
                link("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/" + jobId + "/demo-run-package/download", "application/zip", "Detailed safe job package."),
                link("AI_AUDIT_PACKAGE", "AI audit package", "/api/jobs/" + jobId + "/ai-audit-package/download", "application/zip", "Model-call audit package."),
                link("HANDOFF_PACKAGE", "Handoff package", "/api/jobs/" + jobId + "/handoff-package/download", "application/zip", "Reviewed delivery package."),
                link("OPENAI_SMOKE_PROOF", "OpenAI smoke proof", "/api/jobs/" + jobId + "/openai-smoke-proof", "application/json", "OpenAI proof when provider-backed."),
                link("OPENAI_SMOKE_PROOF_MARKDOWN", "OpenAI smoke proof Markdown", "/api/jobs/" + jobId + "/openai-smoke-proof/markdown/download", "text/markdown", "Provider-backed proof summary as Markdown."),
                link("DEMO_EVIDENCE_CLOSURE", "Demo evidence closure", "/api/jobs/" + jobId + "/demo-evidence-closure", "application/json", "Final proof closure manifest."),
                link("DEMO_EVIDENCE_CLOSURE_MARKDOWN", "Demo evidence closure Markdown", "/api/jobs/" + jobId + "/demo-evidence-closure/markdown/download", "text/markdown", "Final proof closure report."),
                link("DEMO_EVIDENCE_CLOSURE_ZIP", "Demo evidence closure ZIP", "/api/jobs/" + jobId + "/demo-evidence-closure/download", "application/zip", "Final proof closure package.")
        ));
        for (var deliveryLink : narrationDelivery.safeLinks()) {
            links.add(link(deliveryLink.kind(), deliveryLink.label(), deliveryLink.href(), deliveryLink.contentType(), deliveryLink.description()));
        }
        return List.copyOf(links);
    }

    private static List<String> packageEntries(String jobId) {
        return List.of(
                "manifest.json",
                "reviewer-workspace.md",
                "README.md",
                "narration-delivery-package.json",
                "narration-delivery-package.md",
                "final-proof-bundle.json",
                "final-proof-bundle.md",
                "Linked safe route: /api/jobs/" + jobId + "/demo-run-package/download",
                "Linked safe route: /api/jobs/" + jobId + "/ai-audit-package/download",
                "Linked safe route: /api/jobs/" + jobId + "/handoff-package/download",
                "Linked safe route: /api/jobs/" + jobId + "/demo-evidence-closure/download",
                "Linked safe route: /api/jobs/" + jobId + "/openai-smoke-proof/markdown/download",
                "Linked safe route: /api/jobs/" + jobId + "/narration-delivery-package/download"
        );
    }

    private static List<String> safetyNotes() {
        return List.of(
                "Metadata only: no media bytes, transcript bodies, subtitle bodies, local filesystem paths, object storage keys, provider request or response bodies, credentials, bearer tokens, or demo tokens are included.",
                "Reviewer workspace links to existing safe packages instead of embedding generated media artifacts.",
                "Use acceptance gate for final go/no-go, OpenAI smoke proof for provider-backed call evidence, and this workspace as the reviewer table of contents."
        );
    }

    private byte[] zipBytes(DemoReviewerWorkspaceVo workspace, String markdown) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeEntry(zipOutputStream, "manifest.json", manifest(workspace));
            writeEntry(zipOutputStream, "reviewer-workspace.md", markdown);
            writeEntry(zipOutputStream, "narration-delivery-package.json", narrationDeliveryJson(workspace));
            writeEntry(zipOutputStream, "narration-delivery-package.md", narrationDeliveryMarkdown(workspace));
            writeEntry(zipOutputStream, "final-proof-bundle.json", finalProofJson(workspace));
            writeEntry(zipOutputStream, "final-proof-bundle.md", finalProofMarkdown(workspace));
            writeEntry(zipOutputStream, "README.md", readme(workspace));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build demo reviewer workspace package", ex);
        }
        return outputStream.toByteArray();
    }

    private String manifest(DemoReviewerWorkspaceVo workspace) {
        return """
                {"jobId":"%s","overallStatus":"%s","phase":"%s","entries":["manifest.json","reviewer-workspace.md","narration-delivery-package.json","narration-delivery-package.md","final-proof-bundle.json","final-proof-bundle.md","README.md"],"safeLinks":%d}
                """.formatted(
                value(workspace.jobId()),
                value(workspace.overallStatus()),
                value(workspace.phase()),
                workspace.safeLinks().size()
        );
    }

    private String narrationDeliveryJson(DemoReviewerWorkspaceVo workspace) {
        String status = workspace.sections().stream()
                .filter(section -> "NARRATION_DELIVERY".equals(section.key()))
                .findFirst()
                .map(DemoReviewerWorkspaceSectionVo::status)
                .orElse("UNKNOWN");
        return """
                {"jobId":"%s","status":"%s","source":"narration-delivery-package","href":"/api/jobs/%s/narration-delivery-package"}
                """.formatted(value(workspace.jobId()), value(status), value(workspace.jobId()));
    }

    private String narrationDeliveryMarkdown(DemoReviewerWorkspaceVo workspace) {
        return """
                # Narration delivery package

                Job: %s
                Source route: /api/jobs/%s/narration-delivery-package
                Safety: metadata-only; media bytes and narration text are not embedded here.
                """.formatted(value(workspace.jobId()), value(workspace.jobId()));
    }

    private String finalProofJson(DemoReviewerWorkspaceVo workspace) {
        String status = workspace.sections().stream()
                .filter(section -> "FINAL_PROOF_BUNDLE".equals(section.key()))
                .findFirst()
                .map(DemoReviewerWorkspaceSectionVo::status)
                .orElse("UNKNOWN");
        return """
                {"jobId":"%s","status":"%s","source":"final-proof-bundle","evidenceClosureHref":"/api/jobs/%s/demo-evidence-closure","aiAuditPackageHref":"/api/jobs/%s/ai-audit-package/download","openAiSmokeProofMarkdownHref":"/api/jobs/%s/openai-smoke-proof/markdown/download","baselineMode":"actual-only"}
                """.formatted(value(workspace.jobId()), value(status), value(workspace.jobId()), value(workspace.jobId()), value(workspace.jobId()));
    }

    private String finalProofMarkdown(DemoReviewerWorkspaceVo workspace) {
        return """
                # Final proof bundle

                Job: %s
                Evidence closure: /api/jobs/%s/demo-evidence-closure/download
                AI audit package: /api/jobs/%s/ai-audit-package/download
                OpenAI smoke proof Markdown: /api/jobs/%s/openai-smoke-proof/markdown/download
                Baseline mode: actual-only unless a pre-upload baseline JSON is supplied to the closure endpoint.
                Safety: metadata-only; nested proof ZIP binaries, media bytes, transcript text, subtitle text, provider payloads, local paths, object keys, and secrets are not embedded here.
                """.formatted(value(workspace.jobId()), value(workspace.jobId()), value(workspace.jobId()), value(workspace.jobId()));
    }

    private String readme(DemoReviewerWorkspaceVo workspace) {
        return """
                # Demo Reviewer Workspace Package

                This ZIP contains metadata-only reviewer evidence for job `%s`.

                Included files:
                - `manifest.json`
                - `reviewer-workspace.md`
                - `final-proof-bundle.json`
                - `final-proof-bundle.md`
                - `README.md`

                It links to existing safe packages instead of embedding generated media or raw model content.
                """.formatted(value(workspace.jobId()));
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static DemoReviewerWorkspaceSectionVo section(String key, String title, String status, List<String> facts) {
        return new DemoReviewerWorkspaceSectionVo(key, title, status, facts);
    }

    private static DemoReviewerWorkspaceCheckVo ready(
            String key,
            String label,
            String detail,
            String nextAction,
            boolean required
    ) {
        return new DemoReviewerWorkspaceCheckVo(key, label, "READY", detail, nextAction, required);
    }

    private static DemoReviewerWorkspaceCheckVo attention(
            String key,
            String label,
            String detail,
            String nextAction,
            boolean required
    ) {
        return new DemoReviewerWorkspaceCheckVo(key, label, "ATTENTION", detail, nextAction, required);
    }

    private static DemoReviewerWorkspaceCheckVo blocked(
            String key,
            String label,
            String detail,
            String nextAction,
            boolean required
    ) {
        return new DemoReviewerWorkspaceCheckVo(key, label, "BLOCKED", detail, nextAction, required);
    }

    private static DemoReviewerWorkspaceLinkVo link(
            String kind,
            String label,
            String href,
            String contentType,
            String description
    ) {
        return new DemoReviewerWorkspaceLinkVo(kind, label, href, contentType, description);
    }

    private static boolean isReadyStatus(String status) {
        return "READY".equals(status) || "PASS".equals(status) || "EMPTY".equals(status);
    }

    private static boolean isBlockedStatus(String status) {
        return "BLOCKED".equals(status) || "FAIL".equals(status);
    }

    private static String finalProofStatus(String acceptanceStatus, String certificateStatus, String openAiProofStatus) {
        if (isBlockedStatus(acceptanceStatus) || isBlockedStatus(certificateStatus)) {
            return "BLOCKED";
        }
        if (isReadyStatus(acceptanceStatus) && isReadyStatus(certificateStatus) && isReadyStatus(openAiProofStatus)) {
            return "READY";
        }
        return "ATTENTION";
    }

    private static String value(Object value) {
        return value == null ? "n/a" : String.valueOf(value);
    }
}
