package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitCheckVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitLinkVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitRunVo;
import com.linguaframe.operator.domain.vo.DemoPresentationCockpitVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherCommandVo;
import com.linguaframe.operator.domain.vo.DemoRunLauncherVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardCheckVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardJobVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardLinkVo;
import com.linguaframe.operator.domain.vo.DemoSessionRecoveryBoardVo;
import com.linguaframe.operator.domain.vo.DemoSessionCommandCenterVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.PrivateDemoEvidenceGalleryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalStepVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveCandidateVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoRunArchiveVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionActionVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionBoardVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionCheckVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionJobVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionLinkVo;
import com.linguaframe.operator.service.impl.DemoSessionCommandCenterServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSessionCommandCenterServiceTests {

    private final StubPrivateDemoOperationsService operationsService = new StubPrivateDemoOperationsService();
    private final StubPrivateDemoLaunchRehearsalService launchService = new StubPrivateDemoLaunchRehearsalService();
    private final StubDemoRunLauncherService launcherService = new StubDemoRunLauncherService();
    private final StubDemoPresentationCockpitService cockpitService = new StubDemoPresentationCockpitService();
    private final StubPrivateDemoEvidenceGalleryService galleryService = new StubPrivateDemoEvidenceGalleryService();
    private final StubPrivateDemoRunArchiveService archiveService = new StubPrivateDemoRunArchiveService();
    private final StubModelUsageLedgerService ledgerService = new StubModelUsageLedgerService();
    private final StubDemoSessionRecoveryBoardService recoveryBoardService = new StubDemoSessionRecoveryBoardService();
    private final StubSessionNarrationProductionBoardService narrationProductionBoardService = new StubSessionNarrationProductionBoardService();

    private final DemoSessionCommandCenterService service = new DemoSessionCommandCenterServiceImpl(
            operationsService,
            launchService,
            launcherService,
            cockpitService,
            galleryService,
            archiveService,
            ledgerService,
            recoveryBoardService,
            narrationProductionBoardService
    );

    @Test
    void buildsReadyToPresentCommandCenterForRecommendedRun() {
        DemoSessionCommandCenterVo center = service.commandCenter(null);

        assertThat(center.overallStatus()).isEqualTo("READY");
        assertThat(center.phase()).isEqualTo("READY_TO_PRESENT");
        assertThat(center.recommendedNextAction()).contains("Present job job-best");
        assertThat(center.primaryCommand()).contains("job-best");
        assertThat(center.focusRun().role()).isEqualTo("RECOMMENDED");
        assertThat(center.recommendedCompletedRun().jobId()).isEqualTo("job-best");
        assertThat(center.modelCallCount()).isEqualTo(4);
        assertThat(center.estimatedCostUsd()).isEqualByComparingTo("0.01000000");
        assertThat(center.phases()).extracting("id")
                .containsExactly("operations", "launch", "launcher", "cockpit", "gallery", "archive", "model-usage", "recovery-board", "narration-production");
        assertThat(center.recoveryStatus()).isEqualTo("READY");
        assertThat(center.recoverNowCount()).isZero();
        assertThat(center.recoveryRecommendedNextAction()).contains("No recovery action");
        assertThat(center.narrationProductionStatus()).isEqualTo("READY");
        assertThat(center.narrationReadyCount()).isEqualTo(1);
        assertThat(center.narrationBlockedCount()).isZero();
        assertThat(center.narrationRecommendedNextAction()).contains("Narration production is ready");
        assertThat(center.evidenceLinks()).extracting("href")
                .contains(
                        "/api/operator/demo-session-command-center",
                        "/api/operator/demo-session-recovery-board",
                        "/api/operator/session-narration-production-board",
                        "/api/operator/model-usage-ledger",
                        "/api/jobs/job-best/demo-run-package/download",
                        "/api/jobs/job-best/demo-evidence-closure/download"
                );
    }

    @Test
    void buildsPreUploadCommandCenterBeforeAnyRunExists() {
        cockpitService.cockpit = cockpit("READY", "READY_FOR_UPLOAD", null, null, null);
        galleryService.gallery = gallery("EMPTY", 0, null);
        archiveService.archive = archive("ATTENTION", null);
        ledgerService.ledger = ledger("EMPTY", 0, 0, BigDecimal.ZERO);

        DemoSessionCommandCenterVo center = service.commandCenter(null);

        assertThat(center.overallStatus()).isEqualTo("ATTENTION");
        assertThat(center.phase()).isEqualTo("PRE_UPLOAD");
        assertThat(center.focusRun()).isNull();
        assertThat(center.recommendedNextAction()).contains("launch rehearsal step");
        assertThat(center.primaryCommand()).contains("docker-e2e-tears-of-steel-full");
        assertThat(center.modelCallCount()).isZero();
    }

    @Test
    void prioritizesActiveRunDuringProcessing() {
        DemoPresentationCockpitRunVo active = run("job-active", "PROCESSING", "ATTENTION", null, "TARGET_SUBTITLE_EXPORT");
        cockpitService.cockpit = cockpit("ATTENTION", "RUN_IN_PROGRESS", null, active, run("job-best", "COMPLETED", "READY", "READY", null));

        DemoSessionCommandCenterVo center = service.commandCenter(null);

        assertThat(center.overallStatus()).isEqualTo("ATTENTION");
        assertThat(center.phase()).isEqualTo("ACTIVE_RUN");
        assertThat(center.focusRun().role()).isEqualTo("ACTIVE");
        assertThat(center.focusRun().jobId()).isEqualTo("job-active");
        assertThat(center.recommendedNextAction()).contains("Monitor active job job-active");
    }

    @Test
    void blocksWhenModelUsageLedgerReportsProviderFailures() {
        ledgerService.ledger = ledger("BLOCKED", 3, 2, new BigDecimal("0.00006000"));

        DemoSessionCommandCenterVo center = service.commandCenter(null);

        assertThat(center.overallStatus()).isEqualTo("BLOCKED");
        assertThat(center.phase()).isEqualTo("BLOCKED");
        assertThat(center.failedModelCallCount()).isEqualTo(2);
        assertThat(center.phases())
                .filteredOn(phase -> phase.id().equals("model-usage"))
                .singleElement()
                .satisfies(phase -> {
                    assertThat(phase.status()).isEqualTo("BLOCKED");
                    assertThat(phase.blocking()).isTrue();
                });
        assertThat(center.recommendedNextAction()).contains("Resolve blocking demo session checks");
    }

    @Test
    void blocksWhenRecoveryBoardHasRecoverNowRows() {
        recoveryBoardService.board = recoveryBoard("BLOCKED", 1, 0, 0, 0, "Open stuck-job recovery.");

        DemoSessionCommandCenterVo center = service.commandCenter(null);

        assertThat(center.overallStatus()).isEqualTo("BLOCKED");
        assertThat(center.phase()).isEqualTo("BLOCKED");
        assertThat(center.recoveryStatus()).isEqualTo("BLOCKED");
        assertThat(center.recoverNowCount()).isEqualTo(1);
        assertThat(center.recoveryPrimaryAction().id()).isEqualTo("OPEN_STUCK_RECOVERY");
        assertThat(center.recoveryLinks()).extracting("href")
                .contains("/api/operator/demo-session-recovery-board/markdown/download");
        assertThat(center.phases())
                .filteredOn(phase -> phase.id().equals("recovery-board"))
                .singleElement()
                .satisfies(phase -> {
                    assertThat(phase.status()).isEqualTo("BLOCKED");
                    assertThat(phase.blocking()).isTrue();
                    assertThat(phase.nextAction()).contains("Open stuck-job recovery");
                });
        assertThat(center.recommendedNextAction()).contains("Resolve blocking demo session checks");
    }

    @Test
    void blocksWhenNarrationProductionBoardHasBlockedRows() {
        narrationProductionBoardService.board = narrationProductionBoard("BLOCKED", 0, 1, 0, 0, 1, 0, "Open blocked narration production rows.");

        DemoSessionCommandCenterVo center = service.commandCenter(null);
        String markdown = service.commandCenterMarkdown(null);

        assertThat(center.overallStatus()).isEqualTo("BLOCKED");
        assertThat(center.phase()).isEqualTo("BLOCKED");
        assertThat(center.narrationProductionStatus()).isEqualTo("BLOCKED");
        assertThat(center.narrationNeedsReviewCount()).isEqualTo(1);
        assertThat(center.narrationBlockedCount()).isEqualTo(1);
        assertThat(center.narrationPrimaryAction().key()).isEqualTo("OPEN_SCENE_BOARD");
        assertThat(center.narrationProductionLinks()).extracting("href")
                .contains("/api/operator/session-narration-production-board/markdown/download");
        assertThat(center.phases())
                .filteredOn(phase -> phase.id().equals("narration-production"))
                .singleElement()
                .satisfies(phase -> {
                    assertThat(phase.status()).isEqualTo("BLOCKED");
                    assertThat(phase.blocking()).isTrue();
                    assertThat(phase.nextAction()).contains("Open blocked narration production rows");
                });
        assertThat(markdown)
                .contains("## Narration Production")
                .contains("Blocked: 1")
                .contains("/api/operator/session-narration-production-board");
    }

    @Test
    void marksAttentionWhenNarrationProductionNeedsWorkWithoutBlockedRows() {
        narrationProductionBoardService.board = narrationProductionBoard("ATTENTION", 1, 1, 1, 1, 0, 0, "Finish narration render and review rows.");

        DemoSessionCommandCenterVo center = service.commandCenter(null);

        assertThat(center.overallStatus()).isEqualTo("ATTENTION");
        assertThat(center.phase()).isEqualTo("NEEDS_REVIEW");
        assertThat(center.narrationProductionStatus()).isEqualTo("ATTENTION");
        assertThat(center.narrationReadyCount()).isEqualTo(1);
        assertThat(center.narrationNeedsRenderCount()).isEqualTo(1);
        assertThat(center.narrationNeedsAuthoringCount()).isEqualTo(1);
        assertThat(center.phases())
                .filteredOn(phase -> phase.id().equals("narration-production"))
                .singleElement()
                .satisfies(phase -> {
                    assertThat(phase.status()).isEqualTo("ATTENTION");
                    assertThat(phase.blocking()).isFalse();
                });
    }

    @Test
    void selectedJobFocusesCommandCenter() {
        DemoSessionCommandCenterVo center = service.commandCenter("job-selected");

        assertThat(cockpitService.lastJobId).isEqualTo("job-selected");
        assertThat(center.focusRun().role()).isEqualTo("SELECTED");
        assertThat(center.focusRun().jobId()).isEqualTo("job-selected");
        assertThat(center.primaryCommand()).contains("job-selected");
    }

    @Test
    void rendersMarkdownAndDoesNotExposeUnsafeContent() throws Exception {
        cockpitService.cockpit = cockpit(
                "READY",
                "READY_TO_PRESENT",
                run("job-selected", "COMPLETED", "READY", "READY", null),
                null,
                run("job-best", "COMPLETED", "READY", "READY", null)
        );

        DemoSessionCommandCenterVo center = service.commandCenter("job-selected");
        String markdown = service.commandCenterMarkdown("job-selected");
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(center);

        assertThat(markdown)
                .contains("LinguaFrame Demo Session Command Center")
                .contains("job-selected")
                .contains("Model Usage")
                .contains("Narration Production");
        assertThat(json)
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("Bearer ")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider request payload")
                .doesNotContain("provider response payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("corrected subtitle text");
    }

    private static DemoPresentationCockpitVo cockpit(
            String status,
            String phase,
            DemoPresentationCockpitRunVo selected,
            DemoPresentationCockpitRunVo active,
            DemoPresentationCockpitRunVo recommended
    ) {
        return new DemoPresentationCockpitVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                phase,
                active == null ? "Present job job-best using the acceptance gate." : "Monitor active job " + active.jobId() + ".",
                selected,
                active,
                recommended,
                List.of(new DemoPresentationCockpitCheckVo("ACCEPTANCE_GATE", "Acceptance gate", status, "Ready.", "Present.", "BLOCKED".equals(status))),
                List.of(
                        new DemoPresentationCockpitLinkVo("DEMO_RUN_LAUNCHER", "Demo run launcher", "/api/operator/demo-run-launcher"),
                        new DemoPresentationCockpitLinkVo("RECOMMENDED_DEMO_RUN_PACKAGE", "Recommended demo run package", "/api/jobs/job-best/demo-run-package/download")
                ),
                List.of("Metadata only.")
        );
    }

    private static DemoPresentationCockpitRunVo run(
            String jobId,
            String status,
            String readiness,
            String acceptance,
            String currentStage
    ) {
        return new DemoPresentationCockpitRunVo(
                jobId,
                "video-" + jobId,
                "tears-showcase",
                status,
                readiness,
                acceptance,
                "RUNNING",
                currentStage,
                currentStage == null ? null : 120000L,
                acceptance == null ? "Keep watching this monitor." : "Present this run."
        );
    }

    private static PrivateDemoOperationsVo operations(String status) {
        return new PrivateDemoOperationsVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                8,
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
                7,
                "READY".equals(status) ? 0 : 1,
                "BLOCKED".equals(status) ? 1 : 0,
                "full-tears-demo",
                List.of(new PrivateDemoLaunchRehearsalStepVo(
                        "full-tears-demo",
                        "Run full Tears demo",
                        "READY",
                        "Run the full public sample demo.",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        "/tmp/linguaframe-demo/full-tears",
                        "Export evidence after completion.",
                        false
                )),
                List.of("/api/operator/private-demo/operations"),
                "# Launch rehearsal"
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
                List.of(new DemoRunLauncherCommandVo(
                        "Full Tears demo",
                        "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                        "Run the full configured Tears demo."
                )),
                List.of(),
                "# Launcher"
        );
    }

    private static PrivateDemoEvidenceGalleryVo gallery(String status, int completedCount, String recommendedJobId) {
        return new PrivateDemoEvidenceGalleryVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                completedCount,
                completedCount,
                recommendedJobId,
                List.of(),
                List.of(),
                "# Gallery"
        );
    }

    private static PrivateDemoRunArchiveVo archive(String status, String recommendedJobId) {
        return new PrivateDemoRunArchiveVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                recommendedJobId,
                recommendedJobId == null ? null : "video-" + recommendedJobId,
                recommendedJobId == null ? null : "tears-showcase",
                recommendedJobId == null ? "MISSING" : "READY",
                "READY",
                "READY",
                "full-tears-demo",
                recommendedJobId == null ? 0 : 1,
                recommendedJobId == null ? 0 : 1,
                recommendedJobId == null ? List.of() : List.of(new PrivateDemoRunArchiveCandidateVo(
                        recommendedJobId,
                        "video-" + recommendedJobId,
                        "best.mp4",
                        "tears-showcase",
                        "COMPLETED",
                        "READY",
                        94,
                        new BigDecimal("0.10000000"),
                        4,
                        1,
                        true,
                        List.of("RECOMMENDED")
                )),
                recommendedJobId == null ? List.of() : List.of(new PrivateDemoRunArchiveLinkVo(
                        "Demo run package",
                        "/api/jobs/" + recommendedJobId + "/demo-run-package/download",
                        "application/zip",
                        "Safe package."
                )),
                "# Archive"
        );
    }

    private static ModelUsageLedgerVo ledger(String status, int calls, int failedCalls, BigDecimal cost) {
        return new ModelUsageLedgerVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                20,
                "demo-owner",
                "CONFIGURED_DEMO_OWNER",
                new ModelUsageLedgerSummaryVo(
                        status,
                        calls == 0 ? 0 : 1,
                        calls,
                        failedCalls,
                        calls == 0 ? 0 : 1,
                        calls == 0 ? 0 : 2,
                        calls * 100L,
                        cost,
                        calls == 0 ? 0 : 100L,
                        calls == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(failedCalls).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(calls), 2, java.math.RoundingMode.HALF_UP),
                        "BLOCKED".equals(status) ? "Inspect failed model calls and rerun OpenAI preflight." : "Use ledger evidence."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of("/api/operator/model-usage-ledger", "/api/operator/model-usage-ledger/markdown/download"),
                List.of("Metadata only.")
        );
    }

    private static DemoSessionRecoveryBoardVo recoveryBoard(
            String status,
            int recoverNowCount,
            int watchCount,
            int needsReviewCount,
            int readyCount,
            String nextAction
    ) {
        DemoSessionRecoveryBoardActionVo action = recoverNowCount == 0 ? null : new DemoSessionRecoveryBoardActionVo(
                "OPEN_STUCK_RECOVERY",
                "Open stuck-job recovery",
                "/api/jobs/job-stale/stuck-job-recovery",
                "Inspect per-job recovery evidence.",
                true
        );
        return new DemoSessionRecoveryBoardVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                recoverNowCount == 0 ? "No recoverable jobs." : recoverNowCount + " job needs recovery.",
                nextAction,
                recoverNowCount,
                watchCount,
                readyCount,
                needsReviewCount,
                0,
                action,
                recoverNowCount == 0 ? List.of() : List.of(new DemoSessionRecoveryBoardJobVo(
                        "job-stale",
                        "video-stale",
                        "stale.mp4",
                        "tears-showcase",
                        "QUEUED",
                        null,
                        null,
                        Instant.parse("2026-06-29T07:00:00Z"),
                        Instant.parse("2026-06-29T07:00:00Z"),
                        "RECOVER_NOW",
                        "BLOCKED",
                        "QUEUED_STALE_DISPATCH",
                        null,
                        "Open stuck-job recovery.",
                        List.of(action),
                        List.of(new DemoSessionRecoveryBoardLinkVo("STUCK_RECOVERY", "Stuck job recovery", "/api/jobs/job-stale/stuck-job-recovery", "application/json", "Recovery evidence."))
                )),
                List.of(new DemoSessionRecoveryBoardCheckVo("recovery", "Recovery status", status, nextAction, nextAction, recoverNowCount > 0)),
                List.of(
                        new DemoSessionRecoveryBoardLinkVo("JSON", "Recovery board JSON", "/api/operator/demo-session-recovery-board", "application/json", "Recovery board JSON."),
                        new DemoSessionRecoveryBoardLinkVo("MARKDOWN", "Recovery board Markdown", "/api/operator/demo-session-recovery-board/markdown/download", "text/markdown", "Recovery board Markdown.")
                ),
                List.of("Metadata only."),
                "# Recovery Board"
        );
    }

    private static SessionNarrationProductionBoardVo narrationProductionBoard(
            String status,
            int readyCount,
            int needsReviewCount,
            int needsRenderCount,
            int needsAuthoringCount,
            int blockedCount,
            int notApplicableCount,
            String nextAction
    ) {
        SessionNarrationProductionActionVo action = blockedCount == 0 ? null : new SessionNarrationProductionActionVo(
                "OPEN_SCENE_BOARD",
                "Open scene board",
                "/api/jobs/job-narration-blocked/narration-scene-board",
                "Inspect blocked scene-board checks.",
                true
        );
        return new SessionNarrationProductionBoardVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                status,
                blockedCount == 0 ? "Narration production is ready." : blockedCount + " narration production job is blocked.",
                nextAction,
                25,
                readyCount,
                needsReviewCount,
                needsRenderCount,
                needsAuthoringCount,
                blockedCount,
                notApplicableCount,
                action,
                List.of(new SessionNarrationProductionJobVo(
                        blockedCount == 0 ? "job-narration-ready" : "job-narration-blocked",
                        "video-narration",
                        "zh-CN",
                        "COMPLETED",
                        blockedCount == 0 ? "READY_TO_DELIVER" : "BLOCKED",
                        blockedCount == 0 ? "INFO" : "BLOCKING",
                        Instant.parse("2026-06-29T07:00:00Z"),
                        Instant.parse("2026-06-29T08:00:00Z"),
                        4,
                        BigDecimal.valueOf(100),
                        0,
                        false,
                        1,
                        0,
                        true,
                        true,
                        true,
                        true,
                        blockedCount == 0,
                        blockedCount == 0,
                        true,
                        blockedCount == 0 ? null : "Scene board is blocked.",
                        nextAction,
                        List.of(new SessionNarrationProductionCheckVo("scene", "Scene board", status, nextAction, nextAction, blockedCount > 0)),
                        action == null ? List.of() : List.of(action),
                        List.of(new SessionNarrationProductionLinkVo("SCENE_BOARD", "Narration scene board", "/api/jobs/job-narration-blocked/narration-scene-board", "application/json", "Scene board metadata."))
                )),
                List.of(new SessionNarrationProductionCheckVo("narration-production", "Narration production", status, nextAction, nextAction, blockedCount > 0)),
                List.of(
                        new SessionNarrationProductionLinkVo("JSON", "Session narration production board", "/api/operator/session-narration-production-board", "application/json", "Production board JSON."),
                        new SessionNarrationProductionLinkVo("MARKDOWN", "Session narration production Markdown", "/api/operator/session-narration-production-board/markdown/download", "text/markdown", "Production board Markdown.")
                ),
                List.of("Metadata only."),
                "# Session Narration Production Board"
        );
    }

    private final class StubPrivateDemoOperationsService implements PrivateDemoOperationsService {
        private PrivateDemoOperationsVo operations = DemoSessionCommandCenterServiceTests.operations("READY");

        @Override
        public PrivateDemoOperationsVo operations() {
            return operations;
        }
    }

    private final class StubPrivateDemoLaunchRehearsalService implements PrivateDemoLaunchRehearsalService {
        private PrivateDemoLaunchRehearsalVo launch = launch("READY");

        @Override
        public PrivateDemoLaunchRehearsalVo launchRehearsal() {
            return launch;
        }
    }

    private final class StubDemoRunLauncherService implements DemoRunLauncherService {
        private DemoRunLauncherVo launcher = DemoSessionCommandCenterServiceTests.launcher("READY");

        @Override
        public DemoRunLauncherVo launcher() {
            return launcher;
        }
    }

    private final class StubDemoPresentationCockpitService implements DemoPresentationCockpitService {
        private String lastJobId;
        private DemoPresentationCockpitVo cockpit = DemoSessionCommandCenterServiceTests.cockpit("READY", "READY_TO_PRESENT", null, null, run("job-best", "COMPLETED", "READY", "READY", null));

        @Override
        public DemoPresentationCockpitVo cockpit(String jobId) {
            lastJobId = jobId;
            if ("job-selected".equals(jobId)) {
                return DemoSessionCommandCenterServiceTests.cockpit("READY", "READY_TO_PRESENT", run("job-selected", "COMPLETED", "READY", "READY", null), null, run("job-best", "COMPLETED", "READY", "READY", null));
            }
            return cockpit;
        }
    }

    private final class StubPrivateDemoEvidenceGalleryService implements PrivateDemoEvidenceGalleryService {
        private PrivateDemoEvidenceGalleryVo gallery = gallery("READY", 1, "job-best");

        @Override
        public PrivateDemoEvidenceGalleryVo evidenceGallery(Integer limit) {
            return gallery;
        }
    }

    private final class StubPrivateDemoRunArchiveService implements PrivateDemoRunArchiveService {
        private PrivateDemoRunArchiveVo archive = archive("READY", "job-best");

        @Override
        public PrivateDemoRunArchiveVo runArchive() {
            return archive;
        }
    }

    private final class StubModelUsageLedgerService implements ModelUsageLedgerService {
        private ModelUsageLedgerVo ledger = DemoSessionCommandCenterServiceTests.ledger("READY", 4, 0, new BigDecimal("0.01000000"));

        @Override
        public ModelUsageLedgerVo ledger(Integer limit) {
            return ledger;
        }

        @Override
        public String ledgerMarkdown(Integer limit) {
            return "# Ledger";
        }
    }

    private final class StubDemoSessionRecoveryBoardService implements DemoSessionRecoveryBoardService {
        private DemoSessionRecoveryBoardVo board = recoveryBoard("READY", 0, 0, 0, 1, "No recovery action is needed.");

        @Override
        public DemoSessionRecoveryBoardVo board(Integer limit) {
            return board;
        }

        @Override
        public String boardMarkdown(Integer limit) {
            return board.markdown();
        }
    }

    private final class StubSessionNarrationProductionBoardService implements SessionNarrationProductionBoardService {
        private SessionNarrationProductionBoardVo board = narrationProductionBoard("READY", 1, 0, 0, 0, 0, 0, "Narration production is ready.");

        @Override
        public SessionNarrationProductionBoardVo board(Integer limit) {
            return board;
        }

        @Override
        public String boardMarkdown(Integer limit) {
            return board.markdown();
        }
    }
}
