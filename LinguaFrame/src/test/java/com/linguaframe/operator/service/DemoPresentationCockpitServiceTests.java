package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateCheckVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateLinkVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoRunMonitorVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoRunMonitorService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.DemoSampleMediaCatalogVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveCandidateVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.service.impl.DemoPresentationCockpitServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoPresentationCockpitServiceTests {

    private final StubDemoRunLauncherService launcherService = new StubDemoRunLauncherService();
    private final StubDemoSampleMediaCatalogService sampleService = new StubDemoSampleMediaCatalogService();
    private final StubDemoUploadReadinessService uploadReadinessService = new StubDemoUploadReadinessService();
    private final StubPrivateDemoOperationsService operationsService = new StubPrivateDemoOperationsService();
    private final StubPrivateDemoLaunchRehearsalService launchService = new StubPrivateDemoLaunchRehearsalService();
    private final StubPrivateDemoEvidenceGalleryService galleryService = new StubPrivateDemoEvidenceGalleryService();
    private final StubPrivateDemoRunArchiveService archiveService = new StubPrivateDemoRunArchiveService();
    private final StubRuntimeLiveCheckService liveCheckService = new StubRuntimeLiveCheckService();
    private final StubLocalizationJobQueryService jobQueryService = new StubLocalizationJobQueryService();
    private final StubDemoRunMonitorService monitorService = new StubDemoRunMonitorService();
    private final StubDemoAcceptanceGateService acceptanceGateService = new StubDemoAcceptanceGateService();
    private final DemoPresentationCockpitService service = new DemoPresentationCockpitServiceImpl(
            launcherService,
            sampleService,
            uploadReadinessService,
            operationsService,
            launchService,
            galleryService,
            archiveService,
            liveCheckService,
            jobQueryService,
            monitorService,
            acceptanceGateService
    );

    @Test
    void buildsReadyCockpitForCompletedRecommendedRun() {
        DemoPresentationCockpitVo cockpit = service.cockpit(null);

        assertThat(cockpit.overallStatus()).isEqualTo("READY");
        assertThat(cockpit.phase()).isEqualTo("READY_TO_PRESENT");
        assertThat(cockpit.recommendedNextAction()).contains("Present job job-best");
        assertThat(cockpit.recommendedRun().jobId()).isEqualTo("job-best");
        assertThat(cockpit.recommendedRun().readiness()).isEqualTo("READY");
        assertThat(cockpit.activeRun()).isNull();
        assertThat(cockpit.checks())
                .extracting("key")
                .contains(
                        "DEMO_RUN_LAUNCHER",
                        "UPLOAD_READINESS",
                        "LIVE_DEPENDENCIES",
                        "PRIVATE_DEMO_OPERATIONS",
                        "PRIVATE_DEMO_LAUNCH",
                        "EVIDENCE_GALLERY",
                        "RUN_ARCHIVE",
                        "ACCEPTANCE_GATE"
                );
        assertThat(cockpit.links())
                .extracting("url")
                .contains(
                        "/api/operator/demo-run-launcher",
                        "/api/media/uploads/readiness?demoProfileId=tears-showcase",
                        "/api/operator/private-demo/run-archive",
                        "/api/jobs/job-best/demo-acceptance-gate",
                        "/api/jobs/job-best/demo-run-package/download"
                );
    }

    @Test
    void highlightsActiveRunBeforeCompletedRecommendation() {
        jobQueryService.activeJobs = List.of(job("job-active", "video-active", LocalizationJobStatus.PROCESSING, "tears-showcase"));

        DemoPresentationCockpitVo cockpit = service.cockpit(null);

        assertThat(cockpit.overallStatus()).isEqualTo("ATTENTION");
        assertThat(cockpit.phase()).isEqualTo("RUN_IN_PROGRESS");
        assertThat(cockpit.recommendedNextAction()).contains("Monitor active job job-active");
        assertThat(cockpit.activeRun().jobId()).isEqualTo("job-active");
        assertThat(cockpit.activeRun().status()).isEqualTo("PROCESSING");
        assertThat(cockpit.activeRun().attentionLevel()).isEqualTo("RUNNING");
        assertThat(cockpit.links())
                .extracting("url")
                .contains("/api/jobs/job-active/demo-run-monitor");
    }

    @Test
    void selectedJobEnrichesCockpitWithAcceptanceGate() {
        DemoPresentationCockpitVo cockpit = service.cockpit("job-selected");

        assertThat(cockpit.selectedRun()).isNotNull();
        assertThat(cockpit.selectedRun().jobId()).isEqualTo("job-selected");
        assertThat(cockpit.selectedRun().acceptanceStatus()).isEqualTo("READY");
        assertThat(cockpit.links())
                .extracting("url")
                .contains(
                        "/api/jobs/job-selected/demo-run-monitor",
                        "/api/jobs/job-selected/demo-acceptance-gate",
                        "/api/jobs/job-selected/demo-run-snapshot/download",
                        "/api/jobs/job-selected/demo-presenter-pack"
                );
    }

    @Test
    void blockedDependenciesBlockCockpit() {
        liveCheckService.liveChecks = new RuntimeLiveCheckSummaryVo(false, Instant.parse("2026-06-29T08:00:00Z"), Map.of());
        uploadReadinessService.readiness = uploadReadiness("BLOCKED");

        DemoPresentationCockpitVo cockpit = service.cockpit(null);

        assertThat(cockpit.overallStatus()).isEqualTo("BLOCKED");
        assertThat(cockpit.phase()).isEqualTo("BLOCKED_BEFORE_UPLOAD");
        assertThat(cockpit.recommendedNextAction()).contains("Resolve blocking checks");
        assertThat(cockpit.checks())
                .filteredOn(check -> check.status().equals("BLOCKED"))
                .extracting("key")
                .contains("UPLOAD_READINESS", "LIVE_DEPENDENCIES");
    }

    @Test
    void cockpitJsonIsMetadataOnly() throws Exception {
        DemoPresentationCockpitVo cockpit = service.cockpit("job-selected");

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(cockpit);

        assertThat(json)
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("Bearer ")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("corrected subtitle text");
    }

    private static LocalizationJobSummaryVo job(String jobId, String videoId, LocalizationJobStatus status, String profile) {
        Instant now = Instant.parse("2026-06-29T08:00:00Z");
        return new LocalizationJobSummaryVo(
                jobId,
                videoId,
                jobId + ".mp4",
                "zh-CN",
                "alloy",
                "FORMAL",
                "HIGH_CONTRAST",
                0,
                "",
                "BALANCED",
                profile,
                status,
                now.minusSeconds(120),
                now.minusSeconds(60),
                status == LocalizationJobStatus.COMPLETED ? now : null,
                null,
                null,
                null,
                0,
                new BigDecimal("0.10000000")
        );
    }

    private static DemoUploadReadinessVo uploadReadiness(String status) {
        return new DemoUploadReadinessVo(
                status,
                "demo-owner",
                "tears-showcase",
                Instant.parse("2026-06-29T08:00:00Z"),
                List.of(),
                List.of(),
                List.of("/api/media/uploads/readiness")
        );
    }

    private static PrivateDemoOperationsVo operations(String status) {
        return new PrivateDemoOperationsVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "READY".equals(status) ? 6 : 5,
                "READY".equals(status) ? 0 : 1,
                "BLOCKED".equals(status) ? 1 : 0,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private static PrivateDemoLaunchRehearsalVo launch(String status) {
        return new PrivateDemoLaunchRehearsalVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                6,
                "READY".equals(status) ? 0 : 1,
                "BLOCKED".equals(status) ? 1 : 0,
                "full-tears-demo",
                List.of(),
                List.of("/api/operator/private-demo/operations"),
                "# Launch rehearsal"
        );
    }

    private static PrivateDemoEvidenceGalleryVo gallery(String status) {
        return new PrivateDemoEvidenceGalleryVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                1,
                1,
                "job-best",
                List.of(),
                List.of(),
                "# Gallery"
        );
    }

    private static PrivateDemoRunArchiveVo archive(String status) {
        return new PrivateDemoRunArchiveVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "job-best",
                "video-best",
                "tears-showcase",
                "READY",
                "READY",
                "READY",
                "full-tears-demo",
                1,
                1,
                List.of(new PrivateDemoRunArchiveCandidateVo(
                        "job-best",
                        "video-best",
                        "best.mp4",
                        "tears-showcase",
                        "COMPLETED",
                        "READY",
                        94,
                        new BigDecimal("0.10000000"),
                        4,
                        1,
                        true,
                        List.of("RECOMMENDED", "HANDOFF_READY")
                )),
                List.of(new PrivateDemoRunArchiveLinkVo(
                        "Demo run package",
                        "/api/jobs/job-best/demo-run-package/download",
                        "application/zip",
                        "Safe package."
                )),
                "# Archive"
        );
    }

    private static DemoRunLauncherVo launcher(String status) {
        return new DemoRunLauncherVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                "tears-of-steel-casting",
                "tears-showcase",
                "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                List.of(),
                List.of(),
                List.of(),
                "# Launcher"
        );
    }

    private static DemoAcceptanceGateVo gate(String jobId, String status) {
        return new DemoAcceptanceGateVo(
                jobId,
                "video-" + jobId,
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                LocalizationJobStatus.COMPLETED,
                "zh-CN",
                "tears-showcase",
                "Gate",
                "Summary",
                "Present this run.",
                List.of(new DemoAcceptanceGateCheckVo("JOB_COMPLETED", "Job completed", "PASS", "Completed.", true)),
                List.of(),
                List.of(new DemoAcceptanceGateLinkVo("ACCEPTANCE_GATE_JSON", "Acceptance gate JSON", "/api/jobs/" + jobId + "/demo-acceptance-gate")),
                List.of("Metadata only.")
        );
    }

    private static DemoRunMonitorVo monitor(String jobId, LocalizationJobStatus status) {
        return new DemoRunMonitorVo(
                jobId,
                "video-" + jobId,
                status,
                JobDispatchEventStatus.DISPATCHED,
                Instant.parse("2026-06-29T08:00:00Z"),
                120000L,
                status == LocalizationJobStatus.COMPLETED ? LocalizationJobStage.COMPLETED : LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                status == LocalizationJobStatus.COMPLETED ? 12 : 8,
                12,
                0,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                90000L,
                status == LocalizationJobStatus.COMPLETED ? "OK" : "RUNNING",
                "Monitor summary.",
                status == LocalizationJobStatus.COMPLETED ? "Open acceptance gate." : "Keep watching this monitor.",
                List.of(),
                List.of(),
                "# Monitor"
        );
    }

    private static final class StubDemoRunLauncherService implements DemoRunLauncherService {
        private DemoRunLauncherVo launcher = DemoPresentationCockpitServiceTests.launcher("READY");

        @Override
        public DemoRunLauncherVo launcher() {
            return launcher;
        }
    }

    private static final class StubDemoSampleMediaCatalogService implements DemoSampleMediaCatalogService {
        @Override
        public DemoSampleMediaCatalogVo catalog() {
            return new DemoSampleMediaCatalogVo(
                    Instant.parse("2026-06-29T08:00:00Z"),
                    "READY",
                    300,
                    "tears-of-steel-casting",
                    List.of(),
                    List.of(),
                    List.of(),
                    "# Catalog",
                    List.of()
            );
        }
    }

    private static final class StubDemoUploadReadinessService implements DemoUploadReadinessService {
        private DemoUploadReadinessVo readiness = uploadReadiness("READY");

        @Override
        public DemoUploadReadinessVo getReadiness(String demoProfileId) {
            return readiness;
        }
    }

    private static final class StubPrivateDemoOperationsService implements PrivateDemoOperationsService {
        private PrivateDemoOperationsVo operations = DemoPresentationCockpitServiceTests.operations("READY");

        @Override
        public PrivateDemoOperationsVo operations() {
            return operations;
        }
    }

    private static final class StubPrivateDemoLaunchRehearsalService implements PrivateDemoLaunchRehearsalService {
        private PrivateDemoLaunchRehearsalVo launch = launch("READY");

        @Override
        public PrivateDemoLaunchRehearsalVo launchRehearsal() {
            return launch;
        }
    }

    private static final class StubPrivateDemoEvidenceGalleryService implements PrivateDemoEvidenceGalleryService {
        private PrivateDemoEvidenceGalleryVo gallery = gallery("READY");

        @Override
        public PrivateDemoEvidenceGalleryVo evidenceGallery(Integer limit) {
            return gallery;
        }
    }

    private static final class StubPrivateDemoRunArchiveService implements PrivateDemoRunArchiveService {
        private PrivateDemoRunArchiveVo archive = archive("READY");

        @Override
        public PrivateDemoRunArchiveVo runArchive() {
            return archive;
        }
    }

    private static final class StubRuntimeLiveCheckService implements RuntimeLiveCheckService {
        private RuntimeLiveCheckSummaryVo liveChecks = new RuntimeLiveCheckSummaryVo(true, Instant.parse("2026-06-29T08:00:00Z"), Map.of());

        @Override
        public RuntimeLiveCheckSummaryVo check() {
            return liveChecks;
        }
    }

    private static final class StubLocalizationJobQueryService implements LocalizationJobQueryService {
        private List<LocalizationJobSummaryVo> activeJobs = List.of();

        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            if (status == LocalizationJobStatus.PROCESSING || status == LocalizationJobStatus.QUEUED || status == LocalizationJobStatus.RETRYING) {
                return new LocalizationJobListVo(activeJobs, limit == null ? 20 : limit, offset == null ? 0 : offset, activeJobs.size());
            }
            return new LocalizationJobListVo(List.of(), limit == null ? 20 : limit, offset == null ? 0 : offset, 0);
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.linguaframe.job.domain.vo.JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubDemoRunMonitorService implements DemoRunMonitorService {
        @Override
        public DemoRunMonitorVo buildMonitor(String jobId) {
            LocalizationJobStatus status = "job-active".equals(jobId) ? LocalizationJobStatus.PROCESSING : LocalizationJobStatus.COMPLETED;
            return monitor(jobId, status);
        }

        @Override
        public String buildMarkdownMonitor(String jobId) {
            return "# Monitor";
        }
    }

    private static final class StubDemoAcceptanceGateService implements DemoAcceptanceGateService {
        @Override
        public DemoAcceptanceGateVo buildGate(String jobId) {
            return gate(jobId, "READY");
        }
    }
}
