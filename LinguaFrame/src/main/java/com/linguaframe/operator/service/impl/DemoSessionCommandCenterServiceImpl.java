package com.linguaframe.operator.service.impl;

import com.linguaframe.operator.domain.vo.DemoPresentationCockpitLinkVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitRunVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherCommandVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardLinkVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterEvidenceVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterPhaseVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterRunVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.DemoPresentationCockpitService;
import com.linguaframe.operator.service.DemoRunLauncherService;
import com.linguaframe.operator.service.DemoSessionCommandCenterService;
import com.linguaframe.operator.service.DemoSessionRecoveryBoardService;
import com.linguaframe.operator.service.ModelUsageLedgerService;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import com.linguaframe.operator.service.PrivateDemoRunArchiveService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoSessionCommandCenterServiceImpl implements DemoSessionCommandCenterService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String EMPTY = "EMPTY";

    private final PrivateDemoOperationsService operationsService;
    private final PrivateDemoLaunchRehearsalService launchRehearsalService;
    private final DemoRunLauncherService launcherService;
    private final DemoPresentationCockpitService cockpitService;
    private final PrivateDemoEvidenceGalleryService evidenceGalleryService;
    private final PrivateDemoRunArchiveService runArchiveService;
    private final ModelUsageLedgerService modelUsageLedgerService;
    private final DemoSessionRecoveryBoardService recoveryBoardService;

    public DemoSessionCommandCenterServiceImpl(
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            DemoRunLauncherService launcherService,
            DemoPresentationCockpitService cockpitService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService,
            PrivateDemoRunArchiveService runArchiveService,
            ModelUsageLedgerService modelUsageLedgerService,
            DemoSessionRecoveryBoardService recoveryBoardService
    ) {
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.launcherService = launcherService;
        this.cockpitService = cockpitService;
        this.evidenceGalleryService = evidenceGalleryService;
        this.runArchiveService = runArchiveService;
        this.modelUsageLedgerService = modelUsageLedgerService;
        this.recoveryBoardService = recoveryBoardService;
    }

    @Override
    public DemoSessionCommandCenterVo commandCenter(String jobId) {
        PrivateDemoOperationsVo operations = operationsService.operations();
        PrivateDemoLaunchRehearsalVo launch = launchRehearsalService.launchRehearsal();
        DemoRunLauncherVo launcher = launcherService.launcher();
        DemoPresentationCockpitVo cockpit = cockpitService.cockpit(jobId);
        PrivateDemoEvidenceGalleryVo gallery = evidenceGalleryService.evidenceGallery(20);
        PrivateDemoRunArchiveVo archive = runArchiveService.runArchive();
        ModelUsageLedgerVo ledger = modelUsageLedgerService.ledger(20);
        DemoSessionRecoveryBoardVo recoveryBoard = recoveryBoardService.board(20);

        DemoSessionCommandCenterRunVo selected = run("SELECTED", cockpit.selectedRun());
        DemoSessionCommandCenterRunVo active = run("ACTIVE", cockpit.activeRun());
        DemoSessionCommandCenterRunVo recommended = recommendedRun(cockpit, archive);
        DemoSessionCommandCenterRunVo focus = focusRun(selected, active, recommended);
        List<DemoSessionCommandCenterPhaseVo> phases = phases(operations, launch, launcher, cockpit, gallery, archive, ledger, recoveryBoard);
        String overallStatus = overallStatus(phases, focus, ledger);
        String phase = phase(overallStatus, active, focus, gallery, ledger);
        String nextAction = nextAction(overallStatus, phase, cockpit, launch, launcher, focus);
        List<DemoSessionCommandCenterActionVo> actions = actions(launcher, launch, archive, focus);
        List<DemoSessionCommandCenterEvidenceVo> evidenceLinks = evidenceLinks(cockpit, archive, ledger, recoveryBoard, focus);
        ModelUsageLedgerSummaryVo usage = ledger.summary();

        return new DemoSessionCommandCenterVo(
                Instant.now(),
                overallStatus,
                phase,
                nextAction,
                primaryCommand(actions),
                focus,
                active,
                recommended,
                phases,
                actions,
                evidenceLinks,
                safeStatus(recoveryBoard.overallStatus()),
                recoveryBoard.recoverNowCount(),
                recoveryBoard.watchCount(),
                recoveryBoard.needsReviewCount(),
                recoveryBoard.readyCount(),
                safe(recoveryBoard.recommendedNextAction()),
                recoveryBoard.primaryAction(),
                recoveryBoard.links(),
                usage.estimatedCostUsd(),
                usage.modelCallCount(),
                usage.failedModelCallCount(),
                usage.failureRatePercent(),
                usage.averageLatencyMs(),
                usage.providerCacheHitCount(),
                List.of(
                        "Demo session command center is metadata-only and read-only.",
                        "It excludes media bytes, object keys, local paths, transcripts, subtitles, provider payloads, API keys, bearer tokens, demo tokens, and credentials.",
                        "Use it as a run-day index; use per-job packages for detailed evidence."
                )
        );
    }

    @Override
    public String commandCenterMarkdown(String jobId) {
        DemoSessionCommandCenterVo center = commandCenter(jobId);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# LinguaFrame Demo Session Command Center\n\n");
        markdown.append("- Overall: ").append(center.overallStatus()).append('\n');
        markdown.append("- Phase: ").append(center.phase()).append('\n');
        markdown.append("- Next action: ").append(center.recommendedNextAction()).append('\n');
        markdown.append("- Primary command: ").append(center.primaryCommand()).append('\n');
        markdown.append("- Focus job: ").append(center.focusRun() == null ? "none" : center.focusRun().jobId()).append("\n\n");
        markdown.append("## Recovery\n\n");
        markdown.append("- Status: ").append(center.recoveryStatus()).append('\n');
        markdown.append("- Recover now: ").append(center.recoverNowCount()).append('\n');
        markdown.append("- Watch: ").append(center.watchCount()).append('\n');
        markdown.append("- Needs review: ").append(center.needsReviewCount()).append('\n');
        markdown.append("- Ready: ").append(center.readyCount()).append('\n');
        markdown.append("- Next action: ").append(center.recoveryRecommendedNextAction()).append("\n\n");
        markdown.append("## Phase Checklist\n\n");
        for (DemoSessionCommandCenterPhaseVo phase : center.phases()) {
            markdown.append("- ").append(phase.status()).append(" ").append(phase.label()).append(": ")
                    .append(phase.detail()).append('\n');
            markdown.append("  Next: ").append(phase.nextAction()).append('\n');
        }
        markdown.append("\n## Focus Run\n\n");
        if (center.focusRun() == null) {
            markdown.append("- No selected, active, or recommended completed run is available.\n");
        } else {
            DemoSessionCommandCenterRunVo run = center.focusRun();
            markdown.append("- Role: ").append(run.role()).append('\n');
            markdown.append("- Job: ").append(run.jobId()).append('\n');
            markdown.append("- Status: ").append(run.status()).append('\n');
            markdown.append("- Acceptance: ").append(run.acceptanceStatus()).append('\n');
            markdown.append("- Next: ").append(run.nextAction()).append('\n');
        }
        markdown.append("\n## Model Usage\n\n");
        markdown.append("- Estimated cost: ").append(center.estimatedCostUsd()).append('\n');
        markdown.append("- Model calls: ").append(center.modelCallCount()).append('\n');
        markdown.append("- Failed calls: ").append(center.failedModelCallCount()).append('\n');
        markdown.append("- Failure rate: ").append(center.failureRatePercent()).append("%\n");
        markdown.append("- Average latency ms: ").append(center.averageLatencyMs()).append('\n');
        markdown.append("- Provider cache hits: ").append(center.providerCacheHitCount()).append('\n');
        markdown.append("\n## Evidence Links\n\n");
        for (DemoSessionCommandCenterEvidenceVo evidence : center.evidenceLinks()) {
            markdown.append("- ").append(evidence.label()).append(": ").append(evidence.href()).append('\n');
        }
        markdown.append("\n## Commands\n\n");
        for (DemoSessionCommandCenterActionVo action : center.actions()) {
            markdown.append("- ").append(action.primary() ? "PRIMARY " : "")
                    .append(action.label()).append(": ").append(action.command()).append('\n');
        }
        markdown.append("\n## Safety Notes\n\n");
        center.safetyNotes().forEach(note -> markdown.append("- ").append(note).append('\n'));
        return markdown.toString();
    }

    private List<DemoSessionCommandCenterPhaseVo> phases(
            PrivateDemoOperationsVo operations,
            PrivateDemoLaunchRehearsalVo launch,
            DemoRunLauncherVo launcher,
            DemoPresentationCockpitVo cockpit,
            PrivateDemoEvidenceGalleryVo gallery,
            PrivateDemoRunArchiveVo archive,
            ModelUsageLedgerVo ledger,
            DemoSessionRecoveryBoardVo recoveryBoard
    ) {
        List<DemoSessionCommandCenterPhaseVo> phases = new ArrayList<>();
        phases.add(phase("operations", "Operations readiness", operations.overallStatus(),
                "Ready " + operations.readyCount() + ", attention " + operations.attentionCount() + ", blocked " + operations.blockedCount() + ".",
                BLOCKED.equals(operations.overallStatus()) ? "Resolve private-demo operations blockers." : "Keep operations report with the demo evidence.",
                BLOCKED.equals(operations.overallStatus())));
        phases.add(phase("launch", "Launch rehearsal", launch.overallStatus(),
                "Recommended next step is " + safe(launch.recommendedNextStepId()) + ".",
                BLOCKED.equals(launch.overallStatus()) ? "Resolve launch rehearsal blockers." : "Follow the rehearsal order before spending provider budget.",
                BLOCKED.equals(launch.overallStatus())));
        phases.add(phase("launcher", "Demo run launcher", launcher.overallStatus(),
                "Recommended command is " + safe(launcher.recommendedNextCommand()) + ".",
                BLOCKED.equals(launcher.overallStatus()) ? "Fix launcher gates before upload." : "Use launcher command for the next full demo run.",
                BLOCKED.equals(launcher.overallStatus())));
        phases.add(phase("cockpit", "Presentation cockpit", cockpit.overallStatus(),
                "Cockpit phase is " + safe(cockpit.phase()) + ".",
                cockpit.recommendedNextAction(),
                BLOCKED.equals(cockpit.overallStatus())));
        phases.add(phase("gallery", "Evidence gallery", gallery.overallStatus(),
                "Completed jobs " + gallery.completedJobCount() + ", handoff ready " + gallery.handoffReadyCount() + ".",
                gallery.completedJobCount() == 0 ? "Complete a demo run before presentation." : "Use the recommended completed run for handoff.",
                BLOCKED.equals(gallery.overallStatus())));
        phases.add(phase("archive", "Run archive", archive.overallStatus(),
                "Archive recommends " + safe(archive.recommendedJobId()) + ".",
                archive.recommendedJobId() == null ? "Wait for a completed run before archiving." : "Keep archive links with reviewer evidence.",
                BLOCKED.equals(archive.overallStatus())));
        phases.add(phase("model-usage", "Model usage ledger", ledger.summary().ledgerStatus(),
                "Calls " + ledger.summary().modelCallCount() + ", failed " + ledger.summary().failedModelCallCount() + ", cost " + ledger.summary().estimatedCostUsd() + ".",
                ledger.summary().recommendedNextAction(),
                BLOCKED.equals(ledger.summary().ledgerStatus())));
        phases.add(phase("recovery-board", "Session recovery board", recoveryBoard.overallStatus(),
                "Recover now " + recoveryBoard.recoverNowCount() + ", watch " + recoveryBoard.watchCount() + ", needs review " + recoveryBoard.needsReviewCount() + ", ready " + recoveryBoard.readyCount() + ".",
                recoveryBoard.recommendedNextAction(),
                BLOCKED.equals(recoveryBoard.overallStatus())));
        return List.copyOf(phases);
    }

    private String overallStatus(List<DemoSessionCommandCenterPhaseVo> phases, DemoSessionCommandCenterRunVo focus, ModelUsageLedgerVo ledger) {
        if (phases.stream().anyMatch(DemoSessionCommandCenterPhaseVo::blocking)) {
            return BLOCKED;
        }
        if (phases.stream().anyMatch(phase -> ATTENTION.equals(phase.status()))) {
            return ATTENTION;
        }
        if (focus == null && EMPTY.equals(ledger.summary().ledgerStatus())) {
            return EMPTY;
        }
        return READY;
    }

    private String phase(
            String overallStatus,
            DemoSessionCommandCenterRunVo active,
            DemoSessionCommandCenterRunVo focus,
            PrivateDemoEvidenceGalleryVo gallery,
            ModelUsageLedgerVo ledger
    ) {
        if (BLOCKED.equals(overallStatus)) {
            return "BLOCKED";
        }
        if (active != null) {
            return "ACTIVE_RUN";
        }
        if (focus != null && READY.equals(focus.acceptanceStatus())) {
            return "READY_TO_PRESENT";
        }
        if (gallery.completedJobCount() > 0 || ledger.summary().modelCallCount() > 0) {
            return "POST_RUN_REVIEW";
        }
        return "PRE_UPLOAD";
    }

    private String nextAction(
            String overallStatus,
            String phase,
            DemoPresentationCockpitVo cockpit,
            PrivateDemoLaunchRehearsalVo launch,
            DemoRunLauncherVo launcher,
            DemoSessionCommandCenterRunVo focus
    ) {
        if (BLOCKED.equals(overallStatus)) {
            return "Resolve blocking demo session checks before uploading, presenting, or exporting evidence.";
        }
        if ("ACTIVE_RUN".equals(phase) && focus != null) {
            return "Monitor active job " + focus.jobId() + " until it reaches a terminal state.";
        }
        if ("READY_TO_PRESENT".equals(phase) && focus != null) {
            return "Present job " + focus.jobId() + " using the run package, acceptance gate, model ledger, and closure evidence.";
        }
        if ("POST_RUN_REVIEW".equals(phase)) {
            return "Review the recommended completed run and export final evidence packages.";
        }
        if (launch.recommendedNextStepId() != null && !launch.recommendedNextStepId().isBlank()) {
            return "Run launch rehearsal step " + safe(launch.recommendedNextStepId()) + " before " + safe(launcher.recommendedNextCommand()) + ".";
        }
        return cockpit.recommendedNextAction();
    }

    private List<DemoSessionCommandCenterActionVo> actions(
            DemoRunLauncherVo launcher,
            PrivateDemoLaunchRehearsalVo launch,
            PrivateDemoRunArchiveVo archive,
            DemoSessionCommandCenterRunVo focus
    ) {
        Map<String, DemoSessionCommandCenterActionVo> actions = new LinkedHashMap<>();
        add(actions, action("launcher", "Run recommended demo", launcher.recommendedNextCommand(),
                "Recommended sample/profile command from demo run launcher.", focus == null));
        launch.steps().stream()
                .filter(step -> step.id().equals(launch.recommendedNextStepId()))
                .findFirst()
                .ifPresent(step -> add(actions, action("launch-next", "Run launch rehearsal next step", step.command(), step.detail(), focus == null)));
        if (focus != null) {
            add(actions, action("session-command-center", "Export command center", "LINGUAFRAME_DEMO_JOB_ID=" + focus.jobId() + " scripts/demo/demo-session-command-center.sh",
                    "Export focused session command center JSON and Markdown.", true));
            add(actions, action("evidence-closure", "Export evidence closure", "LINGUAFRAME_DEMO_JOB_ID=" + focus.jobId() + " scripts/demo/demo-evidence-closure.sh",
                    "Export final reviewer closure package.", false));
        }
        if (archive.recommendedJobId() != null && (focus == null || !archive.recommendedJobId().equals(focus.jobId()))) {
            add(actions, action("archive-recommended", "Export archive recommended run", "LINGUAFRAME_DEMO_JOB_ID=" + archive.recommendedJobId() + " scripts/demo/demo-session-command-center.sh",
                    "Export command center for archive recommended run.", false));
        }
        return List.copyOf(actions.values());
    }

    private List<DemoSessionCommandCenterEvidenceVo> evidenceLinks(
            DemoPresentationCockpitVo cockpit,
            PrivateDemoRunArchiveVo archive,
            ModelUsageLedgerVo ledger,
            DemoSessionRecoveryBoardVo recoveryBoard,
            DemoSessionCommandCenterRunVo focus
    ) {
        Map<String, DemoSessionCommandCenterEvidenceVo> links = new LinkedHashMap<>();
        add(links, evidence("Command center", "/api/operator/demo-session-command-center", "application/json",
                "Current demo session command center."));
        add(links, evidence("Command center Markdown", "/api/operator/demo-session-command-center/markdown/download", "text/markdown",
                "Downloadable command center notes."));
        for (DemoSessionRecoveryBoardLinkVo link : recoveryBoard.links()) {
            add(links, evidence(link.label(), link.href(), link.contentType(), link.description()));
        }
        add(links, evidence("Model usage ledger", "/api/operator/model-usage-ledger", "application/json",
                "Cross-job model usage, cost, latency, and failure evidence."));
        for (String link : ledger.safeLinks()) {
            add(links, evidence("Model usage ledger link", link, "application/json", "Safe model usage evidence route."));
        }
        for (DemoPresentationCockpitLinkVo link : cockpit.links()) {
            add(links, evidence(link.label(), link.url(), "application/json", link.kind()));
        }
        for (PrivateDemoRunArchiveLinkVo link : archive.archiveLinks()) {
            add(links, evidence(link.label(), link.href(), link.contentType(), link.description()));
        }
        if (focus != null) {
            add(links, evidence("Focused demo run package", "/api/jobs/" + focus.jobId() + "/demo-run-package/download", "application/zip",
                    "Focused run package."));
            add(links, evidence("Focused evidence closure", "/api/jobs/" + focus.jobId() + "/demo-evidence-closure/download", "application/zip",
                    "Focused final closure package."));
        }
        return List.copyOf(links.values());
    }

    private DemoSessionCommandCenterRunVo focusRun(
            DemoSessionCommandCenterRunVo selected,
            DemoSessionCommandCenterRunVo active,
            DemoSessionCommandCenterRunVo recommended
    ) {
        if (selected != null) {
            return selected;
        }
        if (active != null) {
            return active;
        }
        return recommended;
    }

    private DemoSessionCommandCenterRunVo recommendedRun(DemoPresentationCockpitVo cockpit, PrivateDemoRunArchiveVo archive) {
        DemoSessionCommandCenterRunVo cockpitRun = run("RECOMMENDED", cockpit.recommendedRun());
        if (cockpitRun != null) {
            return cockpitRun;
        }
        if (archive.recommendedJobId() == null || archive.recommendedJobId().isBlank()) {
            return null;
        }
        return new DemoSessionCommandCenterRunVo(
                "RECOMMENDED",
                archive.recommendedJobId(),
                archive.recommendedVideoId(),
                archive.recommendedProfileId(),
                null,
                archive.recommendedReadiness(),
                null,
                null,
                null,
                "Open the recommended completed run from the archive."
        );
    }

    private DemoSessionCommandCenterRunVo run(String role, DemoPresentationCockpitRunVo run) {
        if (run == null) {
            return null;
        }
        return new DemoSessionCommandCenterRunVo(
                role,
                run.jobId(),
                run.videoId(),
                run.profileId(),
                run.status(),
                run.readiness(),
                run.acceptanceStatus(),
                run.currentStage(),
                run.elapsedMs(),
                run.nextAction()
        );
    }

    private void add(Map<String, DemoSessionCommandCenterActionVo> actions, DemoSessionCommandCenterActionVo action) {
        actions.putIfAbsent(action.id(), action);
    }

    private void add(Map<String, DemoSessionCommandCenterEvidenceVo> links, DemoSessionCommandCenterEvidenceVo evidence) {
        links.putIfAbsent(evidence.href(), evidence);
    }

    private DemoSessionCommandCenterPhaseVo phase(String id, String label, String status, String detail, String nextAction, boolean blocking) {
        return new DemoSessionCommandCenterPhaseVo(id, safe(label), safeStatus(status), safe(detail), safe(nextAction), blocking);
    }

    private DemoSessionCommandCenterActionVo action(String id, String label, String command, String description, boolean primary) {
        return new DemoSessionCommandCenterActionVo(id, safe(label), safe(command), safe(description), primary);
    }

    private DemoSessionCommandCenterEvidenceVo evidence(String label, String href, String contentType, String description) {
        return new DemoSessionCommandCenterEvidenceVo(safe(label), safe(href), safe(contentType), safe(description));
    }

    private String primaryCommand(List<DemoSessionCommandCenterActionVo> actions) {
        return actions.stream()
                .filter(DemoSessionCommandCenterActionVo::primary)
                .map(DemoSessionCommandCenterActionVo::command)
                .findFirst()
                .orElse(actions.isEmpty() ? "" : actions.getFirst().command());
    }

    private String safeStatus(String value) {
        if (READY.equals(value) || ATTENTION.equals(value) || BLOCKED.equals(value) || EMPTY.equals(value) || "MISSING".equals(value)) {
            return value;
        }
        return ATTENTION;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceAll("/Users/[^\\s,;)]*", "[local-path]")
                .replaceAll("sk-[A-Za-z0-9_-]+", "[redacted]")
                .replace("OPENAI_API_KEY", "[redacted]")
                .replace("private-demo-token", "[redacted]")
                .replace("provider payload", "[redacted]")
                .replace("raw transcript text", "[redacted]")
                .replace("raw subtitle text", "[redacted]")
                .replace("corrected subtitle text", "[redacted]");
    }
}
