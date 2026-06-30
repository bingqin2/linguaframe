package com.linguaframe.operator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.operator.domain.bo.DemoSessionEvidencePackageBo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterEvidenceVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterRunVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessEvidenceVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptActionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptCheckVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptPackageEntryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptSectionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoDeliveryReceiptVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.DemoSessionCommandCenterService;
import com.linguaframe.operator.service.DemoSessionRecoveryBoardService;
import com.linguaframe.operator.service.ModelUsageLedgerService;
import com.linguaframe.operator.service.OpenAiReadinessEvidenceService;
import com.linguaframe.operator.service.PrivateDemoDeliveryReceiptService;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import com.linguaframe.operator.service.PrivateDemoRunArchiveService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class PrivateDemoDeliveryReceiptServiceImpl implements PrivateDemoDeliveryReceiptService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String EMPTY = "EMPTY";
    private static final String CONTENT_TYPE = "application/zip";
    private static final List<String> PACKAGE_FILES = List.of(
            "manifest.json",
            "private-demo-delivery-receipt.json",
            "private-demo-delivery-receipt.md",
            "run-archive.json",
            "command-center.json",
            "README.md"
    );

    private final ObjectMapper objectMapper;
    private final PrivateDemoOperationsService operationsService;
    private final PrivateDemoLaunchRehearsalService launchRehearsalService;
    private final PrivateDemoEvidenceGalleryService evidenceGalleryService;
    private final PrivateDemoRunArchiveService runArchiveService;
    private final DemoSessionCommandCenterService commandCenterService;
    private final DemoSessionRecoveryBoardService recoveryBoardService;
    private final ModelUsageLedgerService modelUsageLedgerService;
    private final OpenAiReadinessEvidenceService openAiReadinessEvidenceService;

    public PrivateDemoDeliveryReceiptServiceImpl(
            ObjectMapper objectMapper,
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService,
            PrivateDemoRunArchiveService runArchiveService,
            DemoSessionCommandCenterService commandCenterService,
            DemoSessionRecoveryBoardService recoveryBoardService,
            ModelUsageLedgerService modelUsageLedgerService,
            OpenAiReadinessEvidenceService openAiReadinessEvidenceService
    ) {
        this.objectMapper = objectMapper;
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.evidenceGalleryService = evidenceGalleryService;
        this.runArchiveService = runArchiveService;
        this.commandCenterService = commandCenterService;
        this.recoveryBoardService = recoveryBoardService;
        this.modelUsageLedgerService = modelUsageLedgerService;
        this.openAiReadinessEvidenceService = openAiReadinessEvidenceService;
    }

    @Override
    public PrivateDemoDeliveryReceiptVo receipt(String jobId) {
        String requestedJobId = normalized(jobId);
        PrivateDemoOperationsVo operations = operationsService.operations();
        PrivateDemoLaunchRehearsalVo launch = launchRehearsalService.launchRehearsal();
        PrivateDemoEvidenceGalleryVo gallery = evidenceGalleryService.evidenceGallery(20);
        PrivateDemoRunArchiveVo archive = runArchiveService.runArchive();
        String focusJobId = requestedJobId != null ? requestedJobId : normalized(archive.recommendedJobId());
        DemoSessionCommandCenterVo commandCenter = commandCenterService.commandCenter(focusJobId);
        DemoSessionRecoveryBoardVo recoveryBoard = recoveryBoardService.board(20);
        ModelUsageLedgerVo ledger = modelUsageLedgerService.ledger(20);
        OpenAiReadinessEvidenceVo openAi = openAiReadinessEvidenceService.getEvidence();
        DemoSessionCommandCenterRunVo focusRun = commandCenter.focusRun();
        String effectiveJobId = focusRun == null ? focusJobId : focusRun.jobId();

        List<PrivateDemoDeliveryReceiptCheckVo> checks = checks(operations, launch, gallery, archive, commandCenter, recoveryBoard, ledger, openAi, effectiveJobId);
        String overallStatus = overallStatus(checks);
        List<PrivateDemoDeliveryReceiptLinkVo> links = links(commandCenter, archive, effectiveJobId);
        List<PrivateDemoDeliveryReceiptPackageEntryVo> packageEntries = packageEntries(effectiveJobId);
        List<PrivateDemoDeliveryReceiptActionVo> actions = actions(commandCenter, effectiveJobId);
        List<PrivateDemoDeliveryReceiptSectionVo> sections = sections(operations, launch, gallery, archive, commandCenter, recoveryBoard, ledger, openAi, effectiveJobId);
        List<String> safetyNotes = safetyNotes();

        PrivateDemoDeliveryReceiptVo base = new PrivateDemoDeliveryReceiptVo(
                Instant.now(),
                overallStatus,
                requestedJobId,
                archive.recommendedJobId(),
                archive.recommendedVideoId(),
                archive.recommendedReadiness(),
                safeStatus(operations.overallStatus()),
                safeStatus(launch.overallStatus()),
                safeStatus(gallery.overallStatus()),
                safeStatus(archive.overallStatus()),
                safeStatus(commandCenter.overallStatus()),
                safeStatus(recoveryBoard.overallStatus()),
                safeStatus(ledger.summary().ledgerStatus()),
                safeStatus(openAi.overallStatus()),
                checks,
                sections,
                actions,
                links,
                packageEntries,
                safetyNotes,
                ""
        );
        return new PrivateDemoDeliveryReceiptVo(
                base.generatedAt(),
                base.overallStatus(),
                base.selectedJobId(),
                base.recommendedJobId(),
                base.recommendedVideoId(),
                base.recommendedReadiness(),
                base.operationsStatus(),
                base.launchStatus(),
                base.galleryStatus(),
                base.archiveStatus(),
                base.commandCenterStatus(),
                base.recoveryStatus(),
                base.modelUsageStatus(),
                base.openAiReadinessStatus(),
                base.checks(),
                base.sections(),
                base.actions(),
                base.evidenceLinks(),
                base.packageEntries(),
                base.safetyNotes(),
                markdown(base)
        );
    }

    @Override
    public String receiptMarkdown(String jobId) {
        return receipt(jobId).receiptNotesMarkdown();
    }

    @Override
    public DemoSessionEvidencePackageBo openPackage(String jobId) {
        String requestedJobId = normalized(jobId);
        PrivateDemoRunArchiveVo archive = runArchiveService.runArchive();
        String focusJobId = requestedJobId != null ? requestedJobId : normalized(archive.recommendedJobId());
        DemoSessionCommandCenterVo commandCenter = commandCenterService.commandCenter(focusJobId);
        PrivateDemoDeliveryReceiptVo receipt = receipt(focusJobId);
        Map<String, Object> manifest = manifest(receipt);

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "manifest.json", writeJson(manifest));
            writeEntry(zipOutputStream, "private-demo-delivery-receipt.json", writeJson(receipt));
            writeEntry(zipOutputStream, "private-demo-delivery-receipt.md", bytes(receipt.receiptNotesMarkdown()));
            writeEntry(zipOutputStream, "run-archive.json", writeJson(archive));
            writeEntry(zipOutputStream, "command-center.json", writeJson(commandCenter));
            writeEntry(zipOutputStream, "README.md", bytes(readme(receipt)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create private demo delivery receipt package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new DemoSessionEvidencePackageBo(filename(receipt), CONTENT_TYPE, content.length, new ByteArrayInputStream(content));
    }

    private List<PrivateDemoDeliveryReceiptCheckVo> checks(
            PrivateDemoOperationsVo operations,
            PrivateDemoLaunchRehearsalVo launch,
            PrivateDemoEvidenceGalleryVo gallery,
            PrivateDemoRunArchiveVo archive,
            DemoSessionCommandCenterVo commandCenter,
            DemoSessionRecoveryBoardVo recoveryBoard,
            ModelUsageLedgerVo ledger,
            OpenAiReadinessEvidenceVo openAi,
            String effectiveJobId
    ) {
        List<PrivateDemoDeliveryReceiptCheckVo> checks = new ArrayList<>();
        checks.add(check("operations", "Operations readiness", operations.overallStatus(),
                "Ready " + operations.readyCount() + ", attention " + operations.attentionCount() + ", blocked " + operations.blockedCount() + ".",
                BLOCKED.equals(operations.overallStatus()) ? "Resolve private-demo operations blockers." : "Keep operations evidence with the receipt.",
                BLOCKED.equals(operations.overallStatus())));
        checks.add(check("launch", "Launch rehearsal", launch.overallStatus(),
                "Recommended next step is " + safe(launch.recommendedNextStepId()) + ".",
                BLOCKED.equals(launch.overallStatus()) ? "Resolve launch rehearsal blockers." : "Keep launch rehearsal evidence with the receipt.",
                BLOCKED.equals(launch.overallStatus())));
        checks.add(check("evidence-gallery", "Evidence gallery", gallery.overallStatus(),
                "Completed jobs " + gallery.completedJobCount() + ", handoff ready " + gallery.handoffReadyCount() + ".",
                gallery.completedJobCount() == 0 ? "Complete at least one demo run before delivering." : "Use the recommended run evidence for handoff.",
                false));
        checks.add(check("run-archive", "Run archive", archive.overallStatus(),
                "Archive recommends " + safe(archive.recommendedJobId()) + " with readiness " + safe(archive.recommendedReadiness()) + ".",
                archive.recommendedJobId() == null ? "Wait for a completed recommended run." : "Use archive links as the delivery table of contents.",
                BLOCKED.equals(archive.overallStatus())));
        checks.add(check("command-center", "Command center", commandCenter.overallStatus(),
                "Phase " + safe(commandCenter.phase()) + ", focus job " + focusJob(commandCenter) + ".",
                commandCenter.recommendedNextAction(),
                BLOCKED.equals(commandCenter.overallStatus())));
        checks.add(check("recovery-board", "Recovery board", recoveryBoard.overallStatus(),
                "Recover now " + recoveryBoard.recoverNowCount() + ", watch " + recoveryBoard.watchCount() + ", ready " + recoveryBoard.readyCount() + ".",
                recoveryBoard.recommendedNextAction(),
                BLOCKED.equals(recoveryBoard.overallStatus())));
        checks.add(check("model-usage", "Model usage ledger", ledger.summary().ledgerStatus(),
                "Calls " + ledger.summary().modelCallCount() + ", failed " + ledger.summary().failedModelCallCount() + ", cost " + ledger.summary().estimatedCostUsd() + ".",
                ledger.summary().recommendedNextAction(),
                BLOCKED.equals(ledger.summary().ledgerStatus())));
        checks.add(check("openai-readiness", "OpenAI readiness evidence", openAi.overallStatus(),
                "Phase " + safe(openAi.phase()) + ".",
                openAi.recommendedNextAction(),
                BLOCKED.equals(openAi.overallStatus())));
        checks.add(check("final-proof-links", "Final proof links", effectiveJobId == null ? ATTENTION : READY,
                effectiveJobId == null ? "No selected or recommended job is available for final proof links." : "Final proof links are available for " + effectiveJobId + ".",
                effectiveJobId == null ? "Complete or select a demo run." : "Export reviewer workspace, handoff portal, evidence closure, OpenAI proof, and AI audit package.",
                false));
        return List.copyOf(checks);
    }

    private List<PrivateDemoDeliveryReceiptSectionVo> sections(
            PrivateDemoOperationsVo operations,
            PrivateDemoLaunchRehearsalVo launch,
            PrivateDemoEvidenceGalleryVo gallery,
            PrivateDemoRunArchiveVo archive,
            DemoSessionCommandCenterVo commandCenter,
            DemoSessionRecoveryBoardVo recoveryBoard,
            ModelUsageLedgerVo ledger,
            OpenAiReadinessEvidenceVo openAi,
            String effectiveJobId
    ) {
        List<PrivateDemoDeliveryReceiptSectionVo> sections = new ArrayList<>();
        sections.add(section("session", "Session delivery", commandCenter.overallStatus(), List.of(
                "Phase: " + safe(commandCenter.phase()),
                "Selected job: " + safe(effectiveJobId),
                "Recommended job: " + safe(archive.recommendedJobId()),
                "Recommended readiness: " + safe(archive.recommendedReadiness())
        )));
        sections.add(section("readiness", "Readiness summary", aggregateStatus(operations.overallStatus(), launch.overallStatus(), openAi.overallStatus()), List.of(
                "Operations: " + safe(operations.overallStatus()),
                "Launch rehearsal: " + safe(launch.overallStatus()),
                "OpenAI readiness: " + safe(openAi.overallStatus())
        )));
        sections.add(section("evidence", "Evidence summary", aggregateStatus(gallery.overallStatus(), archive.overallStatus(), commandCenter.overallStatus()), List.of(
                "Completed jobs: " + gallery.completedJobCount(),
                "Handoff-ready jobs: " + gallery.handoffReadyCount(),
                "Archive links: " + archive.archiveLinks().size(),
                "Command center evidence links: " + commandCenter.evidenceLinks().size()
        )));
        sections.add(section("recovery", "Recovery and usage", aggregateStatus(recoveryBoard.overallStatus(), ledger.summary().ledgerStatus()), List.of(
                "Recovery status: " + safe(recoveryBoard.overallStatus()),
                "Recover now: " + recoveryBoard.recoverNowCount(),
                "Model calls: " + ledger.summary().modelCallCount(),
                "Failed model calls: " + ledger.summary().failedModelCallCount()
        )));
        return List.copyOf(sections);
    }

    private List<PrivateDemoDeliveryReceiptLinkVo> links(
            DemoSessionCommandCenterVo commandCenter,
            PrivateDemoRunArchiveVo archive,
            String effectiveJobId
    ) {
        Map<String, PrivateDemoDeliveryReceiptLinkVo> links = new LinkedHashMap<>();
        add(links, link("Delivery receipt", "/api/operator/private-demo/delivery-receipt", "application/json", "Private demo delivery receipt JSON."));
        add(links, link("Delivery receipt Markdown", "/api/operator/private-demo/delivery-receipt/markdown/download", "text/markdown", "Private demo delivery receipt Markdown."));
        add(links, link("Delivery receipt ZIP", "/api/operator/private-demo/delivery-receipt/download", "application/zip", "Metadata-only receipt ZIP."));
        add(links, link("Session evidence package", "/api/operator/demo-session-evidence-package/download", "application/zip", "Session-level metadata package."));
        add(links, link("Run archive", "/api/operator/private-demo/run-archive", "application/json", "Recommended run archive."));
        for (DemoSessionCommandCenterEvidenceVo evidence : commandCenter.evidenceLinks()) {
            add(links, link(evidence.label(), evidence.href(), evidence.contentType(), evidence.description()));
        }
        for (PrivateDemoRunArchiveLinkVo archiveLink : archive.archiveLinks()) {
            add(links, link(archiveLink.label(), archiveLink.href(), archiveLink.contentType(), archiveLink.description()));
        }
        if (effectiveJobId != null) {
            addJobLinks(links, effectiveJobId);
        }
        return List.copyOf(links.values());
    }

    private void addJobLinks(Map<String, PrivateDemoDeliveryReceiptLinkVo> links, String jobId) {
        String pathJobId = pathSegment(jobId);
        add(links, link("Demo reviewer workspace", "/api/jobs/" + pathJobId + "/demo-reviewer-workspace/download", "application/zip", "Reviewer workspace package."));
        add(links, link("Demo handoff portal", "/api/jobs/" + pathJobId + "/demo-handoff-portal/download", "application/zip", "Static handoff portal package."));
        add(links, link("Demo evidence closure", "/api/jobs/" + pathJobId + "/demo-evidence-closure/download", "application/zip", "Final evidence closure package."));
        add(links, link("AI audit package", "/api/jobs/" + pathJobId + "/ai-audit-package/download", "application/zip", "AI model usage and prompt audit package."));
        add(links, link("OpenAI smoke proof Markdown", "/api/jobs/" + pathJobId + "/openai-smoke-proof/markdown/download", "text/markdown", "Focused OpenAI proof Markdown."));
        add(links, link("Acceptance gate", "/api/jobs/" + pathJobId + "/demo-acceptance-gate", "application/json", "Final acceptance gate."));
        add(links, link("Completion certificate", "/api/jobs/" + pathJobId + "/demo-completion-certificate", "application/json", "Completion certificate."));
    }

    private List<PrivateDemoDeliveryReceiptPackageEntryVo> packageEntries(String effectiveJobId) {
        List<PrivateDemoDeliveryReceiptPackageEntryVo> entries = new ArrayList<>();
        entries.add(entry("Receipt JSON", "private-demo-delivery-receipt.json", "/api/operator/private-demo/delivery-receipt", "application/json", "Receipt metadata."));
        entries.add(entry("Receipt Markdown", "private-demo-delivery-receipt.md", "/api/operator/private-demo/delivery-receipt/markdown/download", "text/markdown", "Receipt notes."));
        entries.add(entry("Receipt ZIP", "linguaframe-private-demo-delivery-receipt.zip", "/api/operator/private-demo/delivery-receipt/download", "application/zip", "Receipt package."));
        entries.add(entry("Session evidence package", "linguaframe-demo-session-evidence-package.zip", "/api/operator/demo-session-evidence-package/download", "application/zip", "Session evidence package."));
        if (effectiveJobId != null) {
            String pathJobId = pathSegment(effectiveJobId);
            entries.add(entry("Reviewer workspace", "demo-reviewer-workspace.zip", "/api/jobs/" + pathJobId + "/demo-reviewer-workspace/download", "application/zip", "Reviewer workspace package."));
            entries.add(entry("Handoff portal", "demo-handoff-portal.zip", "/api/jobs/" + pathJobId + "/demo-handoff-portal/download", "application/zip", "Static handoff portal package."));
            entries.add(entry("Evidence closure", "demo-evidence-closure.zip", "/api/jobs/" + pathJobId + "/demo-evidence-closure/download", "application/zip", "Evidence closure package."));
            entries.add(entry("AI audit package", "ai-audit-package.zip", "/api/jobs/" + pathJobId + "/ai-audit-package/download", "application/zip", "AI audit package."));
            entries.add(entry("OpenAI smoke proof", "openai-smoke-proof.md", "/api/jobs/" + pathJobId + "/openai-smoke-proof/markdown/download", "text/markdown", "OpenAI smoke proof Markdown."));
        }
        return List.copyOf(entries);
    }

    private List<PrivateDemoDeliveryReceiptActionVo> actions(DemoSessionCommandCenterVo commandCenter, String effectiveJobId) {
        Map<String, PrivateDemoDeliveryReceiptActionVo> actions = new LinkedHashMap<>();
        add(actions, action("export-receipt", "Export delivery receipt", command(effectiveJobId, "scripts/demo/private-demo-delivery-receipt.sh"),
                "Export JSON, Markdown, and ZIP receipt artifacts.", true));
        add(actions, action("export-session-evidence", "Export session evidence package", command(effectiveJobId, "scripts/demo/demo-session-evidence-package.sh"),
                "Export session evidence package for the focused or recommended run.", false));
        for (DemoSessionCommandCenterActionVo action : commandCenter.actions()) {
            add(actions, action(action.id(), action.label(), action.command(), action.description(), false));
        }
        return List.copyOf(actions.values());
    }

    private String markdown(PrivateDemoDeliveryReceiptVo receipt) {
        StringBuilder builder = new StringBuilder("# LinguaFrame Private Demo Delivery Receipt\n\n");
        builder.append("- Overall: ").append(receipt.overallStatus()).append('\n');
        builder.append("- Selected job: ").append(safe(receipt.selectedJobId())).append('\n');
        builder.append("- Recommended job: ").append(safe(receipt.recommendedJobId())).append('\n');
        builder.append("- Recommended readiness: ").append(safe(receipt.recommendedReadiness())).append("\n\n");
        builder.append("## Checks\n\n");
        for (PrivateDemoDeliveryReceiptCheckVo check : receipt.checks()) {
            builder.append("- ").append(check.status()).append(" ").append(check.label()).append(": ").append(check.detail()).append('\n');
            builder.append("  Next: ").append(check.nextAction()).append('\n');
        }
        builder.append("\n## Sections\n\n");
        for (PrivateDemoDeliveryReceiptSectionVo section : receipt.sections()) {
            builder.append("### ").append(section.title()).append(" - ").append(section.status()).append("\n\n");
            for (String fact : section.facts()) {
                builder.append("- ").append(fact).append('\n');
            }
            builder.append('\n');
        }
        builder.append("## Evidence Links\n\n");
        for (PrivateDemoDeliveryReceiptLinkVo link : receipt.evidenceLinks()) {
            builder.append("- ").append(link.label()).append(": ").append(link.href()).append('\n');
        }
        builder.append("\n## Package Entries\n\n");
        for (PrivateDemoDeliveryReceiptPackageEntryVo entry : receipt.packageEntries()) {
            builder.append("- ").append(entry.label()).append(": ").append(entry.href()).append('\n');
        }
        builder.append("\n## Actions\n\n");
        for (PrivateDemoDeliveryReceiptActionVo action : receipt.actions()) {
            builder.append("- ").append(action.primary() ? "PRIMARY " : "").append(action.label()).append(": ").append(action.command()).append('\n');
        }
        builder.append("\n## Safety Notes\n\n");
        receipt.safetyNotes().forEach(note -> builder.append("- ").append(note).append('\n'));
        return builder.toString();
    }

    private Map<String, Object> manifest(PrivateDemoDeliveryReceiptVo receipt) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("packageType", "PRIVATE_DEMO_DELIVERY_RECEIPT");
        value.put("generatedAt", Instant.now());
        value.put("overallStatus", receipt.overallStatus());
        value.put("selectedJobId", receipt.selectedJobId());
        value.put("recommendedJobId", receipt.recommendedJobId());
        value.put("entryCount", PACKAGE_FILES.size());
        value.put("entries", PACKAGE_FILES);
        value.put("safeLinks", receipt.evidenceLinks().stream().map(PrivateDemoDeliveryReceiptLinkVo::href).toList());
        value.put("safetyNotes", safetyNotes());
        return value;
    }

    private String readme(PrivateDemoDeliveryReceiptVo receipt) {
        return String.join("\n",
                "# LinguaFrame Private Demo Delivery Receipt",
                "",
                "- Overall: " + receipt.overallStatus(),
                "- Selected job: " + safe(receipt.selectedJobId()),
                "- Recommended job: " + safe(receipt.recommendedJobId()),
                "",
                "## Contents",
                "",
                "- `private-demo-delivery-receipt.json` / `private-demo-delivery-receipt.md`: delivery receipt metadata and notes.",
                "- `run-archive.json`: recommended completed run archive.",
                "- `command-center.json`: focused session command center.",
                "",
                "## Safety",
                "",
                "This ZIP is metadata-only. It links to existing packages instead of embedding nested ZIP binaries, media, transcripts, subtitles, provider payloads, local paths, object keys, tokens, API keys, or credentials.",
                ""
        );
    }

    private String overallStatus(List<PrivateDemoDeliveryReceiptCheckVo> checks) {
        if (checks.stream().anyMatch(PrivateDemoDeliveryReceiptCheckVo::blocking)) {
            return BLOCKED;
        }
        if (checks.stream().anyMatch(check -> ATTENTION.equals(check.status()) || EMPTY.equals(check.status()))) {
            return ATTENTION;
        }
        return READY;
    }

    private String aggregateStatus(String... statuses) {
        for (String status : statuses) {
            if (BLOCKED.equals(status)) {
                return BLOCKED;
            }
        }
        for (String status : statuses) {
            if (ATTENTION.equals(status) || EMPTY.equals(status)) {
                return ATTENTION;
            }
        }
        return READY;
    }

    private List<String> safetyNotes() {
        return List.of(
                "Private demo delivery receipt is metadata-only and read-only.",
                "It excludes media bytes, object keys, local paths, transcripts, subtitles, provider payloads, API keys, bearer tokens, demo tokens, passwords, and credentials.",
                "It links to existing evidence packages and does not embed nested ZIP binaries."
        );
    }

    private PrivateDemoDeliveryReceiptCheckVo check(String id, String label, String status, String detail, String nextAction, boolean blocking) {
        return new PrivateDemoDeliveryReceiptCheckVo(id, safe(label), safeStatus(status), safe(detail), safe(nextAction), blocking);
    }

    private PrivateDemoDeliveryReceiptSectionVo section(String id, String title, String status, List<String> facts) {
        return new PrivateDemoDeliveryReceiptSectionVo(id, safe(title), safeStatus(status), facts.stream().map(this::safe).toList());
    }

    private PrivateDemoDeliveryReceiptLinkVo link(String label, String href, String contentType, String description) {
        return new PrivateDemoDeliveryReceiptLinkVo(safe(label), safe(href), safe(contentType), safe(description));
    }

    private PrivateDemoDeliveryReceiptPackageEntryVo entry(String label, String filename, String href, String contentType, String description) {
        return new PrivateDemoDeliveryReceiptPackageEntryVo(safe(label), safe(filename), safe(href), safe(contentType), safe(description));
    }

    private PrivateDemoDeliveryReceiptActionVo action(String id, String label, String command, String description, boolean primary) {
        return new PrivateDemoDeliveryReceiptActionVo(safe(id), safe(label), safe(command), safe(description), primary);
    }

    private void add(Map<String, PrivateDemoDeliveryReceiptLinkVo> links, PrivateDemoDeliveryReceiptLinkVo link) {
        links.putIfAbsent(link.href(), link);
    }

    private void add(Map<String, PrivateDemoDeliveryReceiptActionVo> actions, PrivateDemoDeliveryReceiptActionVo action) {
        actions.putIfAbsent(action.id(), action);
    }

    private String command(String effectiveJobId, String script) {
        if (effectiveJobId == null) {
            return script;
        }
        return "LINGUAFRAME_DEMO_JOB_ID=" + effectiveJobId + " " + script;
    }

    private String focusJob(DemoSessionCommandCenterVo commandCenter) {
        return commandCenter.focusRun() == null ? "none" : safe(commandCenter.focusRun().jobId());
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write private demo delivery receipt JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        ZipEntry zipEntry = new ZipEntry(name);
        zipOutputStream.putNextEntry(zipEntry);
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String filename(PrivateDemoDeliveryReceiptVo receipt) {
        String jobId = receipt.selectedJobId() != null ? receipt.selectedJobId() : receipt.recommendedJobId();
        if (jobId == null) {
            return "linguaframe-private-demo-delivery-receipt.zip";
        }
        return "linguaframe-private-demo-%s-delivery-receipt.zip".formatted(safeFilenamePart(jobId));
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safeStatus(String value) {
        if (READY.equals(value) || ATTENTION.equals(value) || BLOCKED.equals(value) || EMPTY.equals(value) || "MISSING".equals(value)) {
            return value;
        }
        return ATTENTION;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value
                .replaceAll("/Users/[^\\s,;)]*", "[local-path]")
                .replaceAll("sk-[A-Za-z0-9_-]+", "[redacted]")
                .replace("OPENAI_API_KEY", "[redacted]")
                .replace("private-demo-token", "[redacted]")
                .replace("provider request payload", "[redacted]")
                .replace("provider payload", "[redacted]")
                .replace("raw transcript text", "[redacted]")
                .replace("raw subtitle text", "[redacted]")
                .replace("corrected subtitle text", "[redacted]");
    }

    private String safeFilenamePart(String value) {
        String safeValue = safe(value).replaceAll("[^A-Za-z0-9._-]", "-");
        return safeValue.isBlank() ? "session" : safeValue;
    }

    private String pathSegment(String value) {
        return URLEncoder.encode(safe(value), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
