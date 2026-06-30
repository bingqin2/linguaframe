package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.StoredDemoHandoffPortalPackageBo;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoCompletionCertificateVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalCheckVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalLinkVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalSectionVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.DemoRunSnapshotVo;
import com.linguaframe.job.domain.vo.DemoShareSheetVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.OpenAiSmokeProofVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoCompletionCertificateService;
import com.linguaframe.job.service.DemoHandoffPortalService;
import com.linguaframe.job.service.DemoReviewerWorkspaceService;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.DemoRunSnapshotService;
import com.linguaframe.job.service.DemoShareSheetService;
import com.linguaframe.job.service.LocalizationJobQueryService;
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
public class DemoHandoffPortalServiceImpl implements DemoHandoffPortalService {

    private static final String TIMED_AUDIO_BED = "TIMED_AUDIO_BED";
    private static final String DUCKED_ORIGINAL_AUDIO = "DUCKED_ORIGINAL_AUDIO";

    private final LocalizationJobQueryService queryService;
    private final DemoReviewerWorkspaceService reviewerWorkspaceService;
    private final DemoAcceptanceGateService acceptanceGateService;
    private final DemoCompletionCertificateService completionCertificateService;
    private final DeliveryManifestService deliveryManifestService;
    private final DemoRunSnapshotService runSnapshotService;
    private final DemoShareSheetService shareSheetService;
    private final DemoRunMonitorService runMonitorService;
    private final OpenAiSmokeProofService openAiSmokeProofService;
    private final NarrationMixSettingsRepository mixSettingsRepository;
    private final Clock clock;

    @Autowired
    public DemoHandoffPortalServiceImpl(
            LocalizationJobQueryService queryService,
            DemoReviewerWorkspaceService reviewerWorkspaceService,
            DemoAcceptanceGateService acceptanceGateService,
            DemoCompletionCertificateService completionCertificateService,
            DeliveryManifestService deliveryManifestService,
            DemoRunSnapshotService runSnapshotService,
            DemoShareSheetService shareSheetService,
            DemoRunMonitorService runMonitorService,
            OpenAiSmokeProofService openAiSmokeProofService,
            NarrationMixSettingsRepository mixSettingsRepository
    ) {
        this(queryService, reviewerWorkspaceService, acceptanceGateService, completionCertificateService,
                deliveryManifestService, runSnapshotService, shareSheetService, runMonitorService,
                openAiSmokeProofService, mixSettingsRepository, Clock.systemUTC());
    }

    public DemoHandoffPortalServiceImpl(
            LocalizationJobQueryService queryService,
            DemoReviewerWorkspaceService reviewerWorkspaceService,
            DemoAcceptanceGateService acceptanceGateService,
            DemoCompletionCertificateService completionCertificateService,
            DeliveryManifestService deliveryManifestService,
            DemoRunSnapshotService runSnapshotService,
            DemoShareSheetService shareSheetService,
            DemoRunMonitorService runMonitorService,
            OpenAiSmokeProofService openAiSmokeProofService,
            NarrationMixSettingsRepository mixSettingsRepository,
            Clock clock
    ) {
        this.queryService = queryService;
        this.reviewerWorkspaceService = reviewerWorkspaceService;
        this.acceptanceGateService = acceptanceGateService;
        this.completionCertificateService = completionCertificateService;
        this.deliveryManifestService = deliveryManifestService;
        this.runSnapshotService = runSnapshotService;
        this.shareSheetService = shareSheetService;
        this.runMonitorService = runMonitorService;
        this.openAiSmokeProofService = openAiSmokeProofService;
        this.mixSettingsRepository = mixSettingsRepository;
        this.clock = clock;
    }

    @Override
    public DemoHandoffPortalVo getPortal(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        DemoReviewerWorkspaceVo reviewerWorkspace = reviewerWorkspaceService.getWorkspace(jobId);
        DemoAcceptanceGateVo acceptance = acceptanceGateService.buildGate(jobId);
        DemoCompletionCertificateVo certificate = completionCertificateService.buildCertificate(jobId);
        DeliveryManifestVo manifest = deliveryManifestService.buildManifest(jobId);
        DemoRunSnapshotVo snapshot = runSnapshotService.buildSnapshot(jobId);
        DemoShareSheetVo shareSheet = shareSheetService.buildShareSheet(jobId);
        DemoRunMonitorVo monitor = runMonitorService.buildMonitor(jobId);
        OpenAiSmokeProofVo openAiProof = openAiSmokeProofService.getProof(jobId);
        List<DemoHandoffPortalCheckVo> checks = checks(job, reviewerWorkspace, acceptance, certificate, manifest, snapshot, shareSheet, monitor, openAiProof);
        String status = overallStatus(checks);
        return new DemoHandoffPortalVo(
                job.jobId(),
                job.videoId(),
                Instant.now(clock),
                status,
                phase(status),
                headline(status, shareSheet),
                recommendedNextAction(status),
                job.completedAt(),
                job.targetLanguage(),
                job.demoProfileId(),
                checks,
                sections(job, reviewerWorkspace, acceptance, certificate, manifest, snapshot, shareSheet, monitor, openAiProof),
                safeLinks(job.jobId()),
                packageEntries(job.jobId()),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        DemoHandoffPortalVo portal = getPortal(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Demo Handoff Portal");
        lines.add("");
        lines.add("## Summary");
        lines.add("- Job: " + value(portal.jobId()));
        lines.add("- Video: " + value(portal.videoId()));
        lines.add("- Overall status: " + value(portal.overallStatus()));
        lines.add("- Phase: " + value(portal.phase()));
        lines.add("- Headline: " + value(portal.headline()));
        lines.add("- Target language: " + value(portal.targetLanguage()));
        lines.add("- Demo profile: " + value(portal.demoProfileId()));
        lines.add("- Completed at: " + value(portal.completedAt()));
        lines.add("- Recommended next action: " + value(portal.recommendedNextAction()));
        lines.add("");
        lines.add("## Checks");
        for (DemoHandoffPortalCheckVo check : portal.checks()) {
            lines.add("- " + check.label() + ": " + check.status()
                    + " - " + value(check.detail())
                    + " Next: " + value(check.nextAction()));
        }
        lines.add("");
        lines.add("## Sections");
        for (DemoHandoffPortalSectionVo section : portal.sections()) {
            lines.add("### " + section.title());
            lines.add("- Status: " + value(section.status()));
            for (String fact : section.facts()) {
                lines.add("- " + value(fact));
            }
            lines.add("");
        }
        lines.add("## Package Inventory");
        for (String entry : portal.packageEntries()) {
            lines.add("- " + value(entry));
        }
        lines.add("");
        lines.add("## Safe Links");
        for (DemoHandoffPortalLinkVo link : portal.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : portal.safetyNotes()) {
            lines.add("- " + value(note));
        }
        lines.add("");
        return String.join("\n", lines);
    }

    @Override
    public StoredDemoHandoffPortalPackageBo openPackage(String jobId) {
        DemoHandoffPortalVo portal = getPortal(jobId);
        String markdown = renderMarkdown(jobId);
        byte[] content = zipBytes(portal, markdown);
        return new StoredDemoHandoffPortalPackageBo(
                "linguaframe-job-" + portal.jobId() + "-demo-handoff-portal.zip",
                "application/zip",
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private List<DemoHandoffPortalCheckVo> checks(
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
        List<DemoHandoffPortalCheckVo> checks = new ArrayList<>();
        checks.add(job.status() == LocalizationJobStatus.COMPLETED
                ? ready("JOB_COMPLETED", "Job completed", "Job reached COMPLETED.", "Keep this portal with the completed job id.", true)
                : blocked("JOB_COMPLETED", "Job completed", "Job status is " + job.status() + ".", "Wait for completion before sharing the portal.", true));
        checks.add(statusCheck("REVIEWER_WORKSPACE", "Demo reviewer workspace", reviewerWorkspace.overallStatus(), true,
                "Reviewer workspace status is " + reviewerWorkspace.overallStatus() + ".",
                reviewerWorkspace.recommendedNextAction()));
        checks.add(statusCheck("ACCEPTANCE_GATE", "Acceptance gate", acceptance.gateStatus(), true,
                "Acceptance gate status is " + acceptance.gateStatus() + ".",
                acceptance.recommendedNextAction()));
        checks.add(statusCheck("COMPLETION_CERTIFICATE", "Completion certificate", certificate.certificateStatus(), true,
                "Completion certificate status is " + certificate.certificateStatus() + ".",
                certificate.recommendedNextAction()));
        checks.add(manifest.handoffReady()
                ? ready("DELIVERY_HANDOFF", "Delivery handoff", "Delivery manifest is handoff-ready.", "Use handoff package for reviewed deliverables.", true)
                : blocked("DELIVERY_HANDOFF", "Delivery handoff", "Delivery manifest is not handoff-ready.", "Review delivery manifest before sharing.", true));
        checks.add(ready("PORTAL_PACKAGE", "Static handoff portal ZIP", "Portal package entries and safe links are available.", "Open index.html from the ZIP for offline review.", true));
        checks.add(statusCheck("DEMO_SNAPSHOT", "Demo snapshot", snapshot.readiness(), false,
                "Demo snapshot readiness is " + snapshot.readiness() + ".",
                "Use snapshot when reviewers need an offline static entry point."));
        checks.add(statusCheck("SHARE_SHEET", "Demo share sheet", shareSheet.readiness(), false,
                "Demo share sheet readiness is " + shareSheet.readiness() + ".",
                shareSheet.recommendedNextAction()));
        checks.add(statusCheck("RUN_MONITOR", "Demo run monitor", monitor.attentionLevel(), false,
                "Demo run monitor attention level is " + monitor.attentionLevel() + ".",
                monitor.recommendedNextAction()));
        checks.add(statusCheck("OPENAI_SMOKE_PROOF", "OpenAI smoke proof", openAiProof.overallStatus(), false,
                "OpenAI smoke proof status is " + openAiProof.overallStatus() + ".",
                openAiProof.recommendedNextAction()));
        return List.copyOf(checks);
    }

    private List<DemoHandoffPortalSectionVo> sections(
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
        NarrationMixSettingsSupport.ResolvedNarrationMixSettings mixSettings =
                NarrationMixSettingsSupport.resolve(mixSettingsRepository, job.jobId());
        return List.of(
                section("REVIEWER_WORKSPACE", "Reviewer workspace", reviewerWorkspace.overallStatus(), List.of(
                        "Reviewer workspace phase: " + value(reviewerWorkspace.phase()),
                        "Reviewer checks: " + reviewerWorkspace.checks().size(),
                        "Reviewer safe links: " + reviewerWorkspace.safeLinks().size()
                )),
                section("OFFLINE_PORTAL", "Offline portal", "READY", List.of(
                        "Entry point: index.html",
                        "Package entries: " + packageEntries(job.jobId()).size(),
                        "Static package excludes media bytes and transcript bodies."
                )),
                section("NARRATION_AUDIO_MIX", "Narration audio mix", "READY", List.of(
                        "Audio layout: " + TIMED_AUDIO_BED,
                        "Time aligned: true",
                        "Video mix mode: " + DUCKED_ORIGINAL_AUDIO,
                        "Ducking volume: " + mixSettings.duckingVolume(),
                        "Narration volume: " + mixSettings.narrationVolume(),
                        "Fade duration ms: " + mixSettings.fadeDurationMs(),
                        "Mix settings source: " + mixSettings.source(),
                        "Narration evidence package link is available."
                )),
                section("PRESENTATION_EVIDENCE", "Presentation evidence", shareSheet.readiness(), List.of(
                        "Share sheet: " + value(shareSheet.headline()),
                        "Snapshot readiness: " + value(snapshot.readiness()),
                        "Run monitor: " + value(monitor.attentionLevel()),
                        "OpenAI proof: " + value(openAiProof.overallStatus())
                )),
                section("SAFE_PACKAGES", "Safe packages", certificate.certificateStatus(), List.of(
                        "Acceptance gate: " + acceptance.gateStatus(),
                        "Completion certificate: " + certificate.certificateStatus(),
                        "Delivery handoff ready: " + manifest.handoffReady(),
                        "Demo run package link is available.",
                        "AI audit package link is available.",
                        "Subtitle review evidence package link is available.",
                        "Narration evidence package link is available.",
                        "Handoff package link is available."
                ))
        );
    }

    private byte[] zipBytes(DemoHandoffPortalVo portal, String markdown) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeEntry(zipOutputStream, "index.html", html(portal));
            writeEntry(zipOutputStream, "manifest.json", manifest(portal));
            writeEntry(zipOutputStream, "handoff-portal.md", markdown);
            writeEntry(zipOutputStream, "reviewer-workspace.json", reviewerWorkspaceJson(portal));
            writeEntry(zipOutputStream, "README.md", readme(portal));
            writeEntry(zipOutputStream, "acceptance-gate.json", snapshotJson(portal, "acceptance-gate"));
            writeEntry(zipOutputStream, "completion-certificate.json", snapshotJson(portal, "completion-certificate"));
            writeEntry(zipOutputStream, "share-sheet.json", snapshotJson(portal, "share-sheet"));
            writeEntry(zipOutputStream, "run-monitor.json", snapshotJson(portal, "run-monitor"));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build demo handoff portal package", ex);
        }
        return outputStream.toByteArray();
    }

    private String html(DemoHandoffPortalVo portal) {
        StringBuilder builder = new StringBuilder();
        builder.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n");
        builder.append("<title>LinguaFrame Demo Handoff Portal</title>\n");
        builder.append("<style>body{font-family:Arial,sans-serif;margin:32px;line-height:1.5;color:#1f2933}section{margin:24px 0}code{background:#eef2f7;padding:2px 4px;border-radius:4px}li{margin:6px 0}</style>\n");
        builder.append("</head>\n<body>\n");
        builder.append("<h1>LinguaFrame Demo Handoff Portal</h1>\n");
        builder.append("<p><strong>").append(escape(portal.overallStatus())).append("</strong> ")
                .append(escape(portal.phase())).append("</p>\n");
        builder.append("<p>").append(escape(portal.headline())).append("</p>\n");
        builder.append("<section><h2>Summary</h2><ul>");
        builder.append("<li>Job: <code>").append(escape(portal.jobId())).append("</code></li>");
        builder.append("<li>Video: <code>").append(escape(portal.videoId())).append("</code></li>");
        builder.append("<li>Language: ").append(escape(portal.targetLanguage())).append("</li>");
        builder.append("<li>Profile: ").append(escape(value(portal.demoProfileId()))).append("</li>");
        builder.append("</ul></section>\n");
        builder.append("<section><h2>Checks</h2><ul>");
        for (DemoHandoffPortalCheckVo check : portal.checks()) {
            builder.append("<li><strong>").append(escape(check.status())).append("</strong> ")
                    .append(escape(check.label())).append(": ")
                    .append(escape(check.detail())).append("</li>");
        }
        builder.append("</ul></section>\n");
        builder.append("<section><h2>Sections</h2>");
        for (DemoHandoffPortalSectionVo section : portal.sections()) {
            builder.append("<h3>").append(escape(section.title())).append("</h3><ul>");
            builder.append("<li>Status: ").append(escape(section.status())).append("</li>");
            for (String fact : section.facts()) {
                builder.append("<li>").append(escape(fact)).append("</li>");
            }
            builder.append("</ul>");
        }
        builder.append("</section>\n");
        builder.append("<section><h2>Safe Links</h2><ul>");
        for (DemoHandoffPortalLinkVo link : portal.safeLinks()) {
            builder.append("<li><a href=\"").append(escape(link.href())).append("\">")
                    .append(escape(link.label())).append("</a> - ")
                    .append(escape(link.description())).append("</li>");
        }
        builder.append("</ul></section>\n");
        builder.append("<section><h2>Package Entries</h2><ul>");
        for (String entry : portal.packageEntries()) {
            builder.append("<li>").append(escape(entry)).append("</li>");
        }
        builder.append("</ul></section>\n");
        builder.append("</body>\n</html>\n");
        return builder.toString();
    }

    private String manifest(DemoHandoffPortalVo portal) {
        return """
                {"jobId":"%s","videoId":"%s","overallStatus":"%s","phase":"%s","entries":["index.html","manifest.json","handoff-portal.md","reviewer-workspace.json","README.md"],"safeLinks":%d}
                """.formatted(
                json(portal.jobId()),
                json(portal.videoId()),
                json(portal.overallStatus()),
                json(portal.phase()),
                portal.safeLinks().size()
        );
    }

    private String reviewerWorkspaceJson(DemoHandoffPortalVo portal) {
        return """
                {"jobId":"%s","overallStatus":"%s","source":"demo-reviewer-workspace","href":"/api/jobs/%s/demo-reviewer-workspace"}
                """.formatted(json(portal.jobId()), json(portal.overallStatus()), json(portal.jobId()));
    }

    private String snapshotJson(DemoHandoffPortalVo portal, String kind) {
        return """
                {"jobId":"%s","kind":"%s","source":"safe-%s","generatedAt":"%s"}
                """.formatted(json(portal.jobId()), json(kind), json(kind), json(portal.generatedAt()));
    }

    private String readme(DemoHandoffPortalVo portal) {
        return """
                # LinguaFrame Demo Handoff Portal

                Open `index.html` for the offline reviewer entry point.

                Job: %s
                Status: %s

                This package contains metadata and safe links only. It does not include uploaded media, generated media bytes, transcript bodies, subtitle bodies, provider request or response bodies, object storage keys, local paths, credentials, authentication secrets, or demo access secrets.
                """.formatted(value(portal.jobId()), value(portal.overallStatus()));
    }

    private static DemoHandoffPortalCheckVo statusCheck(
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

    private static String overallStatus(List<DemoHandoffPortalCheckVo> checks) {
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
            case "READY" -> "HANDOFF_PORTAL_READY";
            case "ATTENTION" -> "HANDOFF_PORTAL_NEEDS_REVIEW";
            default -> "HANDOFF_PORTAL_BLOCKED";
        };
    }

    private static String headline(String status, DemoShareSheetVo shareSheet) {
        if ("READY".equals(status)) {
            return "Demo handoff portal is ready: " + value(shareSheet.headline());
        }
        if ("ATTENTION".equals(status)) {
            return "Demo handoff portal needs review: " + value(shareSheet.headline());
        }
        return "Demo handoff portal is blocked: " + value(shareSheet.headline());
    }

    private static String recommendedNextAction(String status) {
        return switch (status) {
            case "READY" -> "Download the handoff portal ZIP and share index.html with reviewers.";
            case "ATTENTION" -> "Review optional evidence gaps before sharing the portal.";
            default -> "Resolve blocked handoff checks before sharing this run.";
        };
    }

    private static List<DemoHandoffPortalLinkVo> safeLinks(String jobId) {
        return List.of(
                link("PORTAL_JSON", "Demo handoff portal", "/api/jobs/" + jobId + "/demo-handoff-portal", "application/json", "Portal metadata."),
                link("PORTAL_MARKDOWN", "Demo handoff portal Markdown", "/api/jobs/" + jobId + "/demo-handoff-portal/markdown/download", "text/markdown", "Portal Markdown."),
                link("PORTAL_ZIP", "Demo handoff portal ZIP", "/api/jobs/" + jobId + "/demo-handoff-portal/download", "application/zip", "Static portal package."),
                link("REVIEWER_WORKSPACE", "Demo reviewer workspace", "/api/jobs/" + jobId + "/demo-reviewer-workspace/download", "application/zip", "Reviewer workspace package."),
                link("DEMO_SNAPSHOT", "Demo snapshot", "/api/jobs/" + jobId + "/demo-run-snapshot/download", "application/zip", "Static snapshot package."),
                link("DEMO_RUN_PACKAGE", "Demo run package", "/api/jobs/" + jobId + "/demo-run-package/download", "application/zip", "Detailed safe job package."),
                link("AI_AUDIT_PACKAGE", "AI audit package", "/api/jobs/" + jobId + "/ai-audit-package/download", "application/zip", "Model-call audit package."),
                link("SUBTITLE_REVIEW_EVIDENCE", "Subtitle review evidence", "/api/jobs/" + jobId + "/subtitle-review-evidence", "application/json", "Metadata-only subtitle review evidence."),
                link("SUBTITLE_REVIEW_EVIDENCE_PACKAGE", "Subtitle review evidence package", "/api/jobs/" + jobId + "/subtitle-review-evidence/download", "application/zip", "Review coverage and release-note evidence package."),
                link("NARRATION_EVIDENCE", "Narration evidence", "/api/jobs/" + jobId + "/narration-evidence", "application/json", "Metadata-only narration evidence."),
                link("NARRATION_EVIDENCE_PACKAGE", "Narration evidence package", "/api/jobs/" + jobId + "/narration-evidence/download", "application/zip", "Narration timing and audio readiness evidence package."),
                link("NARRATION_RECOVERY_HANDOFF", "Narration recovery handoff", "/api/jobs/" + jobId + "/narration-recovery-handoff/download", "application/zip", "Offline narration recovery package for blocked acceptance gates."),
                link("NARRATED_VIDEO_GENERATION", "Generate narrated video", "/api/jobs/" + jobId + "/narration-workspace/generate-video", "application/json", "Create the standalone narrated video artifact when narration audio is ready."),
                link("HANDOFF_PACKAGE", "Handoff package", "/api/jobs/" + jobId + "/handoff-package/download", "application/zip", "Reviewed delivery package."),
                link("DIAGNOSTICS", "Diagnostics", "/api/jobs/" + jobId + "/diagnostics/download", "application/json", "Safe diagnostics report."),
                link("EVIDENCE_MARKDOWN", "Evidence Markdown", "/api/jobs/" + jobId + "/evidence/markdown/download", "text/markdown", "Backend evidence report."),
                link("OPENAI_SMOKE_PROOF", "OpenAI smoke proof", "/api/jobs/" + jobId + "/openai-smoke-proof", "application/json", "Provider-backed proof when available.")
        );
    }

    private static List<String> packageEntries(String jobId) {
        return List.of(
                "index.html",
                "manifest.json",
                "handoff-portal.md",
                "reviewer-workspace.json",
                "README.md",
                "acceptance-gate.json",
                "completion-certificate.json",
                "share-sheet.json",
                "run-monitor.json",
                "Linked safe route: /api/jobs/" + jobId + "/subtitle-review-evidence/download",
                "Linked safe route: /api/jobs/" + jobId + "/narration-evidence/download",
                "Linked safe route: /api/jobs/" + jobId + "/narration-recovery-handoff/download",
                "Linked safe route: /api/jobs/" + jobId + "/narration-workspace/generate-video",
                "Linked safe route: /api/jobs/" + jobId + "/demo-reviewer-workspace/download",
                "Linked safe route: /api/jobs/" + jobId + "/demo-run-package/download"
        );
    }

    private static List<String> safetyNotes() {
        return List.of(
                "Metadata only: no media bytes, transcript bodies, subtitle bodies, local filesystem paths, object storage keys, provider request or response bodies, credentials, authentication secrets, or demo access secrets are included.",
                "The portal links to existing safe packages instead of embedding uploaded or generated media artifacts.",
                "Reviewer note bodies stay in the editing UI and are not included in portal packages.",
                "Use reviewer workspace for the compact handoff, demo snapshot for static run browsing, and this portal as the offline entry point."
        );
    }

    private static DemoHandoffPortalSectionVo section(String key, String title, String status, List<String> facts) {
        return new DemoHandoffPortalSectionVo(key, title, status, facts);
    }

    private static DemoHandoffPortalLinkVo link(String kind, String label, String href, String contentType, String description) {
        return new DemoHandoffPortalLinkVo(kind, label, href, contentType, description);
    }

    private static DemoHandoffPortalCheckVo ready(String key, String label, String detail, String nextAction, boolean required) {
        return new DemoHandoffPortalCheckVo(key, label, "READY", detail, nextAction, required);
    }

    private static DemoHandoffPortalCheckVo attention(String key, String label, String detail, String nextAction, boolean required) {
        return new DemoHandoffPortalCheckVo(key, label, "ATTENTION", detail, nextAction, required);
    }

    private static DemoHandoffPortalCheckVo blocked(String key, String label, String detail, String nextAction, boolean required) {
        return new DemoHandoffPortalCheckVo(key, label, "BLOCKED", detail, nextAction, required);
    }

    private static boolean isReadyStatus(String status) {
        return "READY".equals(status)
                || "PASS".equals(status)
                || "COMPLETED".equals(status)
                || "OPENAI_SMOKE_PROVEN".equals(status);
    }

    private static boolean isBlockedStatus(String status) {
        return "BLOCKED".equals(status)
                || "FAIL".equals(status)
                || "FAILED".equals(status)
                || "HANDOFF_BLOCKED".equals(status);
    }

    private static void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static String value(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private static String json(Object value) {
        return value(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String escape(String value) {
        return value(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
