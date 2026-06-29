package com.linguaframe.operator.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.operator.domain.bo.DemoSessionEvidencePackageBo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitCheckVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitLinkVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitRunVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterEvidenceVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterRunVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryDownloadVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryJobVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalStepVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCheckVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCommandVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsSectionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveCandidateVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.DemoPresentationCockpitService;
import com.linguaframe.operator.service.DemoSessionCommandCenterService;
import com.linguaframe.operator.service.DemoSessionEvidencePackageService;
import com.linguaframe.operator.service.ModelUsageLedgerService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DemoSessionEvidencePackageServiceImpl implements DemoSessionEvidencePackageService {

    private static final String CONTENT_TYPE = "application/zip";
    private static final List<String> ENTRIES = List.of(
            "manifest.json",
            "README.md",
            "command-center.json",
            "command-center.md",
            "operations.json",
            "operations.md",
            "launch-rehearsal.json",
            "launch-rehearsal.md",
            "model-usage-ledger.json",
            "model-usage-ledger.md",
            "presentation-cockpit.json",
            "presentation-cockpit.md",
            "evidence-gallery.json",
            "evidence-gallery.md",
            "run-archive.json",
            "run-archive.md"
    );

    private final ObjectMapper objectMapper;
    private final DemoSessionCommandCenterService commandCenterService;
    private final PrivateDemoOperationsService operationsService;
    private final PrivateDemoLaunchRehearsalService launchRehearsalService;
    private final ModelUsageLedgerService modelUsageLedgerService;
    private final DemoPresentationCockpitService cockpitService;
    private final PrivateDemoEvidenceGalleryService evidenceGalleryService;
    private final PrivateDemoRunArchiveService runArchiveService;

    public DemoSessionEvidencePackageServiceImpl(
            ObjectMapper objectMapper,
            DemoSessionCommandCenterService commandCenterService,
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            ModelUsageLedgerService modelUsageLedgerService,
            DemoPresentationCockpitService cockpitService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService,
            PrivateDemoRunArchiveService runArchiveService
    ) {
        this.objectMapper = objectMapper;
        this.commandCenterService = commandCenterService;
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.modelUsageLedgerService = modelUsageLedgerService;
        this.cockpitService = cockpitService;
        this.evidenceGalleryService = evidenceGalleryService;
        this.runArchiveService = runArchiveService;
    }

    @Override
    public DemoSessionEvidencePackageBo openPackage(String jobId) {
        String focusedJobId = normalized(jobId);
        DemoSessionCommandCenterVo commandCenter = commandCenterService.commandCenter(focusedJobId);
        PrivateDemoOperationsVo operations = operationsService.operations();
        PrivateDemoLaunchRehearsalVo launch = launchRehearsalService.launchRehearsal();
        ModelUsageLedgerVo ledger = modelUsageLedgerService.ledger(20);
        DemoPresentationCockpitVo cockpit = cockpitService.cockpit(focusedJobId);
        PrivateDemoEvidenceGalleryVo gallery = evidenceGalleryService.evidenceGallery(20);
        PrivateDemoRunArchiveVo archive = runArchiveService.runArchive();
        Map<String, Object> manifest = manifest(focusedJobId, commandCenter);

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "manifest.json", writeJson(manifest));
            writeEntry(zipOutputStream, "README.md", readmeMarkdown(commandCenter).getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "command-center.json", writeJson(commandCenter));
            writeEntry(zipOutputStream, "command-center.md", bytes(commandCenterService.commandCenterMarkdown(focusedJobId)));
            writeEntry(zipOutputStream, "operations.json", writeJson(operations));
            writeEntry(zipOutputStream, "operations.md", bytes(operationsMarkdown(operations)));
            writeEntry(zipOutputStream, "launch-rehearsal.json", writeJson(launch));
            writeEntry(zipOutputStream, "launch-rehearsal.md", bytes(launchMarkdown(launch)));
            writeEntry(zipOutputStream, "model-usage-ledger.json", writeJson(ledger));
            writeEntry(zipOutputStream, "model-usage-ledger.md", bytes(modelUsageLedgerService.ledgerMarkdown(20)));
            writeEntry(zipOutputStream, "presentation-cockpit.json", writeJson(cockpit));
            writeEntry(zipOutputStream, "presentation-cockpit.md", bytes(cockpitMarkdown(cockpit)));
            writeEntry(zipOutputStream, "evidence-gallery.json", writeJson(gallery));
            writeEntry(zipOutputStream, "evidence-gallery.md", bytes(galleryMarkdown(gallery)));
            writeEntry(zipOutputStream, "run-archive.json", writeJson(archive));
            writeEntry(zipOutputStream, "run-archive.md", bytes(archiveMarkdown(archive)));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create demo session evidence package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new DemoSessionEvidencePackageBo(filename(focusedJobId), CONTENT_TYPE, content.length, new ByteArrayInputStream(content));
    }

    private Map<String, Object> manifest(String focusedJobId, DemoSessionCommandCenterVo commandCenter) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("packageType", "DEMO_SESSION_EVIDENCE_PACKAGE");
        value.put("generatedAt", Instant.now());
        value.put("focusedJobId", focusedJobId);
        value.put("overallStatus", commandCenter.overallStatus());
        value.put("phase", commandCenter.phase());
        value.put("primaryCommand", commandCenter.primaryCommand());
        value.put("entryCount", ENTRIES.size());
        value.put("entries", ENTRIES);
        value.put("safeLinks", commandCenter.evidenceLinks().stream()
                .map(DemoSessionCommandCenterEvidenceVo::href)
                .toList());
        value.put("safetyNotes", List.of(
                "This package is metadata-only and read-only.",
                "It excludes media bytes, object keys, local paths, transcripts, subtitles, provider payloads, API keys, bearer tokens, demo tokens, JWT signing secrets, passwords, and credentials.",
                "Use per-job demo run, AI audit, and handoff packages for detailed run evidence."
        ));
        return value;
    }

    private String readmeMarkdown(DemoSessionCommandCenterVo commandCenter) {
        return String.join("\n",
                "# LinguaFrame Demo Session Evidence Package",
                "",
                "- Overall: " + safe(commandCenter.overallStatus()),
                "- Phase: " + safe(commandCenter.phase()),
                "- Focus job: " + focusJob(commandCenter),
                "- Primary command: " + safe(commandCenter.primaryCommand()),
                "- Next action: " + safe(commandCenter.recommendedNextAction()),
                "",
                "## Contents",
                "",
                "- `command-center.json` / `command-center.md`: run-day status, phase gates, focused run, primary command, and safe evidence links.",
                "- `operations.json` / `operations.md`: private-demo runtime, access, provider, storage, cleanup, and evidence readiness.",
                "- `launch-rehearsal.json` / `launch-rehearsal.md`: ordered pre-demo steps and expected evidence outputs.",
                "- `model-usage-ledger.json` / `model-usage-ledger.md`: cross-job model-call count, cost, latency, failures, and cache evidence.",
                "- `presentation-cockpit.json` / `presentation-cockpit.md`: active or selected run presentation status and links.",
                "- `evidence-gallery.json` / `evidence-gallery.md`: completed run selection and package links.",
                "- `run-archive.json` / `run-archive.md`: recommended completed run and operator-level archive links.",
                "",
                "## Safety",
                "",
                "This ZIP contains metadata only. It does not contain uploaded media, generated media, object storage keys, local filesystem paths, raw transcript text, raw subtitle text, provider payloads, bearer tokens, demo tokens, API keys, passwords, or credentials.",
                "",
                "Use this package as the session-level table of contents. Use per-job demo run packages, AI audit packages, evidence bundles, and reviewed handoff packages for detailed evidence and deliverables.",
                ""
        );
    }

    private String operationsMarkdown(PrivateDemoOperationsVo operations) {
        StringBuilder builder = new StringBuilder("# LinguaFrame Private Demo Operations\n\n");
        builder.append("- Overall: ").append(safe(operations.overallStatus())).append('\n');
        builder.append("- Ready: ").append(operations.readyCount()).append('\n');
        builder.append("- Attention: ").append(operations.attentionCount()).append('\n');
        builder.append("- Blocked: ").append(operations.blockedCount()).append("\n\n");
        builder.append("## Sections\n\n");
        for (PrivateDemoOperationsSectionVo section : operations.sections()) {
            builder.append("### ").append(safe(section.title())).append(" - ").append(safe(section.status())).append("\n\n");
            for (PrivateDemoOperationsCheckVo check : section.checks()) {
                builder.append("- ").append(safe(check.status())).append(" ").append(safe(check.label())).append(": ")
                        .append(safe(check.detail())).append('\n');
                builder.append("  Next: ").append(safe(check.nextAction())).append('\n');
            }
            builder.append('\n');
        }
        builder.append("## Commands\n\n");
        for (PrivateDemoOperationsCommandVo command : operations.commands()) {
            builder.append("- ").append(safe(command.label())).append(": `").append(safe(command.command())).append("`\n");
        }
        return builder.toString();
    }

    private String launchMarkdown(PrivateDemoLaunchRehearsalVo launch) {
        if (launch.rehearsalNotesMarkdown() != null && !launch.rehearsalNotesMarkdown().isBlank()) {
            return launch.rehearsalNotesMarkdown();
        }
        StringBuilder builder = new StringBuilder("# LinguaFrame Private Demo Launch Rehearsal\n\n");
        builder.append("- Overall: ").append(safe(launch.overallStatus())).append('\n');
        builder.append("- Recommended next step: ").append(safe(launch.recommendedNextStepId())).append("\n\n");
        for (PrivateDemoLaunchRehearsalStepVo step : launch.steps()) {
            builder.append("- ").append(safe(step.status())).append(" ").append(safe(step.id())).append(": ")
                    .append(safe(step.title())).append('\n');
            builder.append("  Command: `").append(safe(step.command())).append("`\n");
            builder.append("  Evidence: ").append(safe(step.evidencePath())).append('\n');
            builder.append("  Next: ").append(safe(step.nextAction())).append('\n');
        }
        builder.append("\n## Evidence\n\n");
        for (String download : launch.evidenceDownloads()) {
            builder.append("- ").append(safe(download)).append('\n');
        }
        return builder.toString();
    }

    private String cockpitMarkdown(DemoPresentationCockpitVo cockpit) {
        DemoPresentationCockpitRunVo focus = cockpit.selectedRun() != null ? cockpit.selectedRun()
                : cockpit.activeRun() != null ? cockpit.activeRun() : cockpit.recommendedRun();
        StringBuilder builder = new StringBuilder("# LinguaFrame Demo Presentation Cockpit\n\n");
        builder.append("- Overall: ").append(safe(cockpit.overallStatus())).append('\n');
        builder.append("- Phase: ").append(safe(cockpit.phase())).append('\n');
        builder.append("- Next action: ").append(safe(cockpit.recommendedNextAction())).append('\n');
        builder.append("- Focus job: ").append(focus == null ? "none" : safe(focus.jobId())).append("\n\n");
        builder.append("## Checks\n\n");
        for (DemoPresentationCockpitCheckVo check : cockpit.checks()) {
            builder.append("- ").append(safe(check.status())).append(" ").append(safe(check.label())).append(": ")
                    .append(safe(check.detail())).append('\n');
            builder.append("  Next: ").append(safe(check.nextAction())).append('\n');
        }
        builder.append("\n## Links\n\n");
        for (DemoPresentationCockpitLinkVo link : cockpit.links()) {
            builder.append("- ").append(safe(link.label())).append(": ").append(safe(link.url())).append('\n');
        }
        return builder.toString();
    }

    private String galleryMarkdown(PrivateDemoEvidenceGalleryVo gallery) {
        if (gallery.galleryNotesMarkdown() != null && !gallery.galleryNotesMarkdown().isBlank()) {
            return gallery.galleryNotesMarkdown();
        }
        StringBuilder builder = new StringBuilder("# LinguaFrame Private Demo Evidence Gallery\n\n");
        builder.append("- Overall: ").append(safe(gallery.overallStatus())).append('\n');
        builder.append("- Completed jobs: ").append(gallery.completedJobCount()).append('\n');
        builder.append("- Handoff ready: ").append(gallery.handoffReadyCount()).append('\n');
        builder.append("- Recommended job: ").append(safe(gallery.recommendedJobId())).append("\n\n");
        for (PrivateDemoEvidenceGalleryJobVo job : gallery.jobs()) {
            builder.append("- ").append(safe(job.jobId())).append(": ").append(safe(job.status().name()))
                    .append(", ").append(safe(job.demoProfileId())).append(", handoff ready ").append(job.handoffReady()).append('\n');
            for (PrivateDemoEvidenceGalleryDownloadVo download : job.downloads()) {
                builder.append("  Link: ").append(safe(download.label())).append(" - ").append(safe(download.href())).append('\n');
            }
        }
        return builder.toString();
    }

    private String archiveMarkdown(PrivateDemoRunArchiveVo archive) {
        if (archive.archiveNotesMarkdown() != null && !archive.archiveNotesMarkdown().isBlank()) {
            return archive.archiveNotesMarkdown();
        }
        StringBuilder builder = new StringBuilder("# LinguaFrame Private Demo Run Archive\n\n");
        builder.append("- Overall: ").append(safe(archive.overallStatus())).append('\n');
        builder.append("- Recommended job: ").append(safe(archive.recommendedJobId())).append('\n');
        builder.append("- Operations: ").append(safe(archive.operationsOverallStatus())).append('\n');
        builder.append("- Launch: ").append(safe(archive.launchOverallStatus())).append("\n\n");
        builder.append("## Candidates\n\n");
        for (PrivateDemoRunArchiveCandidateVo candidate : archive.candidates()) {
            builder.append("- ").append(safe(candidate.jobId())).append(": ").append(safe(candidate.readiness()))
                    .append(", ").append(safe(candidate.profileId())).append(", calls ").append(candidate.modelCallCount()).append('\n');
        }
        builder.append("\n## Links\n\n");
        for (PrivateDemoRunArchiveLinkVo link : archive.archiveLinks()) {
            builder.append("- ").append(safe(link.label())).append(": ").append(safe(link.href())).append('\n');
        }
        return builder.toString();
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write demo session evidence package JSON.", ex);
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

    private String filename(String focusedJobId) {
        if (focusedJobId == null) {
            return "linguaframe-demo-session-evidence-package.zip";
        }
        return "linguaframe-demo-session-%s-evidence-package.zip".formatted(safeFilenamePart(focusedJobId));
    }

    private String focusJob(DemoSessionCommandCenterVo commandCenter) {
        DemoSessionCommandCenterRunVo focus = commandCenter.focusRun();
        return focus == null ? "none" : safe(focus.jobId());
    }

    private String normalized(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        return value;
    }

    private String safeFilenamePart(String value) {
        String normalized = safe(value).replaceAll("[^A-Za-z0-9._-]", "-");
        return normalized.isBlank() ? "session" : normalized;
    }
}
