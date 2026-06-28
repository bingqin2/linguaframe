package com.linguaframe.operator.service.impl;

import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitCheckVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitLinkVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitRunVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.DemoPresentationCockpitService;
import com.linguaframe.operator.service.DemoRunLauncherService;
import com.linguaframe.operator.service.DemoSampleMediaCatalogService;
import com.linguaframe.operator.service.PrivateDemoEvidenceGalleryService;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import com.linguaframe.operator.service.PrivateDemoRunArchiveService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DemoPresentationCockpitServiceImpl implements DemoPresentationCockpitService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String MISSING = "MISSING";

    private final DemoRunLauncherService launcherService;
    private final DemoSampleMediaCatalogService sampleMediaCatalogService;
    private final DemoUploadReadinessService uploadReadinessService;
    private final PrivateDemoOperationsService operationsService;
    private final PrivateDemoLaunchRehearsalService launchRehearsalService;
    private final PrivateDemoEvidenceGalleryService evidenceGalleryService;
    private final PrivateDemoRunArchiveService runArchiveService;
    private final RuntimeLiveCheckService liveCheckService;
    private final LocalizationJobQueryService jobQueryService;
    private final DemoRunMonitorService monitorService;
    private final DemoAcceptanceGateService acceptanceGateService;

    public DemoPresentationCockpitServiceImpl(
            DemoRunLauncherService launcherService,
            DemoSampleMediaCatalogService sampleMediaCatalogService,
            DemoUploadReadinessService uploadReadinessService,
            PrivateDemoOperationsService operationsService,
            PrivateDemoLaunchRehearsalService launchRehearsalService,
            PrivateDemoEvidenceGalleryService evidenceGalleryService,
            PrivateDemoRunArchiveService runArchiveService,
            RuntimeLiveCheckService liveCheckService,
            LocalizationJobQueryService jobQueryService,
            DemoRunMonitorService monitorService,
            DemoAcceptanceGateService acceptanceGateService
    ) {
        this.launcherService = launcherService;
        this.sampleMediaCatalogService = sampleMediaCatalogService;
        this.uploadReadinessService = uploadReadinessService;
        this.operationsService = operationsService;
        this.launchRehearsalService = launchRehearsalService;
        this.evidenceGalleryService = evidenceGalleryService;
        this.runArchiveService = runArchiveService;
        this.liveCheckService = liveCheckService;
        this.jobQueryService = jobQueryService;
        this.monitorService = monitorService;
        this.acceptanceGateService = acceptanceGateService;
    }

    @Override
    public DemoPresentationCockpitVo cockpit(String jobId) {
        DemoRunLauncherVo launcher = launcherService.launcher();
        DemoUploadReadinessVo uploadReadiness = uploadReadinessService.getReadiness(launcher.recommendedProfileId());
        PrivateDemoOperationsVo operations = operationsService.operations();
        PrivateDemoLaunchRehearsalVo launch = launchRehearsalService.launchRehearsal();
        PrivateDemoEvidenceGalleryVo gallery = evidenceGalleryService.evidenceGallery(20);
        PrivateDemoRunArchiveVo archive = runArchiveService.runArchive();
        RuntimeLiveCheckSummaryVo liveChecks = liveCheckService.check();

        DemoPresentationCockpitRunVo activeRun = activeRun();
        DemoPresentationCockpitRunVo recommendedRun = recommendedRun(archive);
        DemoPresentationCockpitRunVo selectedRun = selectedRun(jobId);
        List<DemoPresentationCockpitCheckVo> checks = checks(launcher, uploadReadiness, operations, launch, gallery, archive, liveChecks, recommendedRun);
        String status = status(checks, activeRun, selectedRun, recommendedRun);
        String phase = phase(status, activeRun, selectedRun, recommendedRun);

        return new DemoPresentationCockpitVo(
                Instant.now(),
                status,
                phase,
                nextAction(status, activeRun, selectedRun, recommendedRun),
                selectedRun,
                activeRun,
                recommendedRun,
                checks,
                links(launcher, activeRun, selectedRun, recommendedRun),
                List.of(
                        "Metadata-only cockpit: only IDs, statuses, counts, readiness labels, and safe routes are included.",
                        "The cockpit is read-only and does not upload media, start Docker, call providers, create artifacts, retry jobs, or run cleanup."
                )
        );
    }

    private List<DemoPresentationCockpitCheckVo> checks(
            DemoRunLauncherVo launcher,
            DemoUploadReadinessVo uploadReadiness,
            PrivateDemoOperationsVo operations,
            PrivateDemoLaunchRehearsalVo launch,
            PrivateDemoEvidenceGalleryVo gallery,
            PrivateDemoRunArchiveVo archive,
            RuntimeLiveCheckSummaryVo liveChecks,
            DemoPresentationCockpitRunVo recommendedRun
    ) {
        List<DemoPresentationCockpitCheckVo> checks = new ArrayList<>();
        checks.add(check("DEMO_RUN_LAUNCHER", "Demo run launcher", launcher.overallStatus(),
                "Launcher recommends " + safe(launcher.recommendedNextCommand()) + ".",
                READY.equals(launcher.overallStatus()) ? "Run the recommended full-demo command when ready." : "Inspect demo launcher gates before uploading.",
                BLOCKED.equals(launcher.overallStatus())));
        checks.add(check("UPLOAD_READINESS", "Upload readiness", uploadReadiness.overallStatus(),
                "Upload readiness for " + safe(uploadReadiness.demoProfileId()) + " is " + status(uploadReadiness.overallStatus()) + ".",
                BLOCKED.equals(uploadReadiness.overallStatus()) ? "Resolve blocking upload readiness checks before uploading." : "Validate the selected media file before upload.",
                BLOCKED.equals(uploadReadiness.overallStatus())));
        checks.add(check("LIVE_DEPENDENCIES", "Live dependencies", liveChecks.healthy() ? READY : BLOCKED,
                liveChecks.healthy() ? "Live dependency probes are healthy." : "One or more live dependency probes are down.",
                liveChecks.healthy() ? "No live dependency action required." : "Fix live dependency checks before uploading or presenting.",
                !liveChecks.healthy()));
        checks.add(check("PRIVATE_DEMO_OPERATIONS", "Private demo operations", operations.overallStatus(),
                "Operations readiness is " + status(operations.overallStatus()) + ".",
                BLOCKED.equals(operations.overallStatus()) ? "Resolve private-demo operations blockers." : "Keep operations report with demo evidence.",
                BLOCKED.equals(operations.overallStatus())));
        checks.add(check("PRIVATE_DEMO_LAUNCH", "Private demo launch", launch.overallStatus(),
                "Launch rehearsal next step is " + safe(launch.recommendedNextStepId()) + ".",
                BLOCKED.equals(launch.overallStatus()) ? "Resolve launch rehearsal blockers." : "Follow the launch rehearsal order.",
                BLOCKED.equals(launch.overallStatus())));
        checks.add(check("EVIDENCE_GALLERY", "Evidence gallery", gallery.overallStatus(),
                "Completed jobs: " + gallery.completedJobCount() + ", handoff-ready jobs: " + gallery.handoffReadyCount() + ".",
                gallery.completedJobCount() == 0 ? "Run a demo job before presenting." : "Open the recommended completed run.",
                BLOCKED.equals(gallery.overallStatus())));
        checks.add(check("RUN_ARCHIVE", "Run archive", archive.overallStatus(),
                "Run archive recommends " + safe(archive.recommendedJobId()) + ".",
                archive.recommendedJobId() == null ? "Complete a demo run before archiving." : "Keep the run archive with demo evidence.",
                BLOCKED.equals(archive.overallStatus())));
        String acceptanceStatus = recommendedRun == null ? MISSING : recommendedRun.acceptanceStatus();
        checks.add(check("ACCEPTANCE_GATE", "Acceptance gate", acceptanceStatus == null ? MISSING : acceptanceStatus,
                recommendedRun == null ? "No recommended completed run is available." : "Recommended run acceptance is " + acceptanceStatus + ".",
                READY.equals(acceptanceStatus) ? "Present the recommended run." : "Open the acceptance gate and resolve blockers.",
                BLOCKED.equals(acceptanceStatus) || MISSING.equals(acceptanceStatus)));
        return List.copyOf(checks);
    }

    private DemoPresentationCockpitRunVo activeRun() {
        List<LocalizationJobSummaryVo> activeJobs = new ArrayList<>();
        activeJobs.addAll(list(LocalizationJobStatus.PROCESSING));
        activeJobs.addAll(list(LocalizationJobStatus.QUEUED));
        activeJobs.addAll(list(LocalizationJobStatus.RETRYING));
        if (activeJobs.isEmpty()) {
            return null;
        }
        LocalizationJobSummaryVo summary = activeJobs.getFirst();
        DemoRunMonitorVo monitor = monitorService.buildMonitor(summary.jobId());
        return new DemoPresentationCockpitRunVo(
                summary.jobId(),
                summary.videoId(),
                profile(summary.demoProfileId()),
                summary.status().name(),
                ATTENTION,
                null,
                monitor.attentionLevel(),
                monitor.currentStage() == null ? null : monitor.currentStage().name(),
                monitor.elapsedMs(),
                monitor.recommendedNextAction()
        );
    }

    private List<LocalizationJobSummaryVo> list(LocalizationJobStatus status) {
        LocalizationJobListVo list = jobQueryService.listJobs(status, 5, 0);
        return list == null ? List.of() : list.jobs();
    }

    private DemoPresentationCockpitRunVo recommendedRun(PrivateDemoRunArchiveVo archive) {
        if (archive.recommendedJobId() == null || archive.recommendedJobId().isBlank()) {
            return null;
        }
        DemoAcceptanceGateVo gate = acceptanceGateService.buildGate(archive.recommendedJobId());
        return new DemoPresentationCockpitRunVo(
                archive.recommendedJobId(),
                archive.recommendedVideoId(),
                profile(archive.recommendedProfileId()),
                gate.jobStatus().name(),
                archive.recommendedReadiness(),
                gate.gateStatus(),
                null,
                null,
                null,
                gate.recommendedNextAction()
        );
    }

    private DemoPresentationCockpitRunVo selectedRun(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return null;
        }
        DemoRunMonitorVo monitor = monitorService.buildMonitor(jobId);
        DemoAcceptanceGateVo gate = acceptanceGateService.buildGate(jobId);
        return new DemoPresentationCockpitRunVo(
                jobId,
                gate.videoId(),
                profile(gate.demoProfileId()),
                gate.jobStatus().name(),
                gate.gateStatus(),
                gate.gateStatus(),
                monitor.attentionLevel(),
                monitor.currentStage() == null ? null : monitor.currentStage().name(),
                monitor.elapsedMs(),
                gate.recommendedNextAction()
        );
    }

    private String status(List<DemoPresentationCockpitCheckVo> checks, DemoPresentationCockpitRunVo activeRun, DemoPresentationCockpitRunVo selectedRun, DemoPresentationCockpitRunVo recommendedRun) {
        if (checks.stream().anyMatch(DemoPresentationCockpitCheckVo::blocking)) {
            return BLOCKED;
        }
        if (activeRun != null) {
            return ATTENTION;
        }
        DemoPresentationCockpitRunVo focus = selectedRun == null ? recommendedRun : selectedRun;
        if (focus != null && READY.equals(focus.acceptanceStatus())) {
            return READY;
        }
        return ATTENTION;
    }

    private String phase(String status, DemoPresentationCockpitRunVo activeRun, DemoPresentationCockpitRunVo selectedRun, DemoPresentationCockpitRunVo recommendedRun) {
        if (BLOCKED.equals(status)) {
            return "BLOCKED_BEFORE_UPLOAD";
        }
        if (activeRun != null) {
            return "RUN_IN_PROGRESS";
        }
        DemoPresentationCockpitRunVo focus = selectedRun == null ? recommendedRun : selectedRun;
        if (focus != null && READY.equals(focus.acceptanceStatus())) {
            return "READY_TO_PRESENT";
        }
        return "READY_FOR_UPLOAD";
    }

    private String nextAction(String status, DemoPresentationCockpitRunVo activeRun, DemoPresentationCockpitRunVo selectedRun, DemoPresentationCockpitRunVo recommendedRun) {
        if (BLOCKED.equals(status)) {
            return "Resolve blocking checks before upload or presentation.";
        }
        if (activeRun != null) {
            return "Monitor active job " + activeRun.jobId() + " until it reaches a terminal state.";
        }
        DemoPresentationCockpitRunVo focus = selectedRun == null ? recommendedRun : selectedRun;
        if (focus != null && READY.equals(focus.acceptanceStatus())) {
            return "Present job " + focus.jobId() + " using the acceptance gate, run package, and snapshot.";
        }
        return "Run the recommended full-demo command after upload readiness is clear.";
    }

    private List<DemoPresentationCockpitLinkVo> links(
            DemoRunLauncherVo launcher,
            DemoPresentationCockpitRunVo activeRun,
            DemoPresentationCockpitRunVo selectedRun,
            DemoPresentationCockpitRunVo recommendedRun
    ) {
        Map<String, DemoPresentationCockpitLinkVo> links = new LinkedHashMap<>();
        add(links, link("DEMO_COCKPIT_JSON", "Demo cockpit JSON", "/api/operator/demo-presentation-cockpit"));
        add(links, link("DEMO_RUN_LAUNCHER", "Demo run launcher", "/api/operator/demo-run-launcher"));
        add(links, link("UPLOAD_READINESS", "Upload readiness", "/api/media/uploads/readiness?demoProfileId=" + safe(launcher.recommendedProfileId())));
        add(links, link("LIVE_CHECKS", "Live checks", "/api/runtime/live-checks"));
        add(links, link("PRIVATE_DEMO_OPERATIONS", "Private demo operations", "/api/operator/private-demo/operations"));
        add(links, link("PRIVATE_DEMO_EVIDENCE_GALLERY", "Private demo evidence gallery", "/api/operator/private-demo/evidence-gallery"));
        add(links, link("PRIVATE_DEMO_RUN_ARCHIVE", "Private demo run archive", "/api/operator/private-demo/run-archive"));
        if (activeRun != null) {
            runLinks(links, activeRun.jobId(), "ACTIVE");
        }
        if (recommendedRun != null) {
            runLinks(links, recommendedRun.jobId(), "RECOMMENDED");
        }
        if (selectedRun != null) {
            runLinks(links, selectedRun.jobId(), "SELECTED");
        }
        return List.copyOf(links.values());
    }

    private void runLinks(Map<String, DemoPresentationCockpitLinkVo> links, String jobId, String prefix) {
        add(links, link(prefix + "_RUN_MONITOR", prefix + " run monitor", "/api/jobs/" + jobId + "/demo-run-monitor"));
        add(links, link(prefix + "_ACCEPTANCE_GATE", prefix + " acceptance gate", "/api/jobs/" + jobId + "/demo-acceptance-gate"));
        add(links, link(prefix + "_COMPLETION_CERTIFICATE", prefix + " completion certificate", "/api/jobs/" + jobId + "/demo-completion-certificate"));
        add(links, link(prefix + "_REPLAY_CARD", prefix + " replay card", "/api/jobs/" + jobId + "/demo-replay-card"));
        add(links, link(prefix + "_PRESENTER_PACK", prefix + " presenter pack", "/api/jobs/" + jobId + "/demo-presenter-pack"));
        add(links, link(prefix + "_SNAPSHOT", prefix + " snapshot", "/api/jobs/" + jobId + "/demo-run-snapshot/download"));
        add(links, link(prefix + "_DEMO_RUN_PACKAGE", prefix + " demo run package", "/api/jobs/" + jobId + "/demo-run-package/download"));
    }

    private void add(Map<String, DemoPresentationCockpitLinkVo> links, DemoPresentationCockpitLinkVo link) {
        links.putIfAbsent(link.url(), link);
    }

    private DemoPresentationCockpitCheckVo check(String key, String label, String status, String detail, String nextAction, boolean blocking) {
        return new DemoPresentationCockpitCheckVo(key, label, status(status), safe(detail), safe(nextAction), blocking);
    }

    private DemoPresentationCockpitLinkVo link(String kind, String label, String url) {
        return new DemoPresentationCockpitLinkVo(kind, label, safe(url));
    }

    private String status(String value) {
        if (READY.equals(value) || ATTENTION.equals(value) || BLOCKED.equals(value) || MISSING.equals(value)) {
            return value;
        }
        return ATTENTION;
    }

    private String profile(String value) {
        return value == null || value.isBlank() ? "manual" : safe(value);
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
