package com.linguaframe.operator.service.impl;

import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalStepVo;
import com.linguaframe.operator.domain.vo.PrivateDemoLaunchRehearsalVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsSectionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.service.PrivateDemoLaunchRehearsalService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PrivateDemoLaunchRehearsalServiceImpl implements PrivateDemoLaunchRehearsalService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String LAUNCH_ROUTE = "/api/operator/private-demo/launch-rehearsal";
    private static final String OPERATIONS_ROUTE = "/api/operator/private-demo/operations";

    private final PrivateDemoOperationsService operationsService;
    private final RuntimeDependencySummaryService summaryService;

    public PrivateDemoLaunchRehearsalServiceImpl(
            PrivateDemoOperationsService operationsService,
            RuntimeDependencySummaryService summaryService
    ) {
        this.operationsService = operationsService;
        this.summaryService = summaryService;
    }

    @Override
    public PrivateDemoLaunchRehearsalVo launchRehearsal() {
        PrivateDemoOperationsVo operations = operationsService.operations();
        RuntimeDependencySummaryVo summary = summaryService.getSummary();
        Map<String, String> sectionStatuses = operations.sections().stream()
                .collect(java.util.stream.Collectors.toMap(
                        PrivateDemoOperationsSectionVo::title,
                        PrivateDemoOperationsSectionVo::status,
                        (left, right) -> left
                ));

        List<PrivateDemoLaunchRehearsalStepVo> steps = List.of(
                deployPreflight(summary),
                stackStartup(summary),
                privatePreflight(operations, sectionStatuses),
                openAiPreflight(sectionStatuses),
                backupDryRun(sectionStatuses),
                restoreDryRun(sectionStatuses),
                shortSmokeDemo(sectionStatuses),
                fullTearsDemo(sectionStatuses),
                presenterPackExport(sectionStatuses),
                operationsReportExport(operations)
        );

        long readyCount = steps.stream().filter(step -> READY.equals(step.status())).count();
        long attentionCount = steps.stream().filter(step -> ATTENTION.equals(step.status())).count();
        long blockedCount = steps.stream().filter(step -> BLOCKED.equals(step.status())).count();
        String overallStatus = blockedCount > 0 ? BLOCKED : attentionCount > 0 ? ATTENTION : READY;
        String recommendedNextStepId = steps.stream()
                .filter(step -> !READY.equals(step.status()))
                .min(Comparator.comparingInt(step -> severity(step.status())))
                .map(PrivateDemoLaunchRehearsalStepVo::id)
                .orElse("operations-report-export");

        List<String> evidenceDownloads = List.of(
                OPERATIONS_ROUTE,
                "/api/runtime/dependencies",
                "/api/runtime/live-checks",
                "/api/jobs/{jobId}/demo-presenter-pack",
                "/api/jobs/{jobId}/demo-run-package/download",
                "/api/jobs/{jobId}/ai-audit-package/download",
                "/api/jobs/{jobId}/evidence/bundle/download"
        );

        return new PrivateDemoLaunchRehearsalVo(
                Instant.now(),
                overallStatus,
                readyCount,
                attentionCount,
                blockedCount,
                recommendedNextStepId,
                steps,
                evidenceDownloads,
                notes(overallStatus, recommendedNextStepId, steps, evidenceDownloads)
        );
    }

    private PrivateDemoLaunchRehearsalStepVo deployPreflight(RuntimeDependencySummaryVo summary) {
        boolean hasRoute = summary != null
                && summary.runtime() != null
                && summary.runtime().requiredRoutes().contains(LAUNCH_ROUTE);
        boolean hasMigration = summary != null
                && summary.runtime() != null
                && summary.runtime().latestMigrationVersion() > 0;
        String status = hasRoute && hasMigration ? READY : BLOCKED;
        return step(
                "deploy-preflight",
                "Deployment preflight",
                status,
                hasRoute && hasMigration
                        ? "The backend runtime contract includes launch rehearsal and bundled migrations."
                        : "The deployed backend runtime contract is stale or missing launch rehearsal.",
                "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-deploy-preflight.sh",
                "/api/runtime/dependencies",
                status.equals(READY)
                        ? "Run this before starting the proxy-fronted private demo stack."
                        : "Rebuild and redeploy the backend, then rerun deployment preflight.",
                true
        );
    }

    private PrivateDemoLaunchRehearsalStepVo stackStartup(RuntimeDependencySummaryVo summary) {
        boolean hasStorage = summary != null && summary.storage() != null && hasText(summary.storage().bucket());
        return step(
                "stack-startup",
                "Proxy-fronted stack startup",
                hasStorage ? READY : BLOCKED,
                hasStorage
                        ? "Runtime metadata includes object storage and expected backend dependencies."
                        : "Runtime metadata is incomplete for private demo startup.",
                "docker compose --env-file .env.private-demo -f docker-compose.yml -f deploy/private-demo/docker-compose.private-demo.yml up -d --build",
                "docs/deployment/private-demo.md",
                hasStorage
                        ? "Start the stack manually after deploy preflight passes."
                        : "Fix private-demo env values before starting the stack.",
                true
        );
    }

    private PrivateDemoLaunchRehearsalStepVo privatePreflight(
            PrivateDemoOperationsVo operations,
            Map<String, String> sectionStatuses
    ) {
        String runtimeStatus = sectionStatuses.getOrDefault("Runtime contract", READY);
        String liveStatus = sectionStatuses.getOrDefault("Live dependencies", READY);
        String accessStatus = sectionStatuses.getOrDefault("Access gate", READY);
        String status = worst(runtimeStatus, liveStatus, accessStatus, operations.overallStatus().equals(BLOCKED) ? BLOCKED : READY);
        return step(
                "private-preflight",
                "Private demo preflight",
                status,
                "Checks owner access, frontend/backend reachability, runtime freshness, dependencies, and sample paths.",
                "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-preflight.sh",
                "/api/operator/private-demo/operations",
                status.equals(READY)
                        ? "Run before uploading media or inviting a viewer."
                        : "Fix blocked operations readiness sections before uploading media.",
                true
        );
    }

    private PrivateDemoLaunchRehearsalStepVo openAiPreflight(Map<String, String> sectionStatuses) {
        String providerStatus = sectionStatuses.getOrDefault("Provider readiness", ATTENTION);
        String status = BLOCKED.equals(providerStatus) ? BLOCKED : ATTENTION.equals(providerStatus) ? ATTENTION : READY;
        return step(
                "openai-preflight",
                "OpenAI provider preflight",
                status,
                status.equals(READY)
                        ? "Provider configuration is ready for an explicit OpenAI preflight."
                        : "Provider-backed demo readiness needs manual confirmation before paid calls.",
                "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/openai-demo-preflight.sh",
                "/api/runtime/live-checks",
                status.equals(READY)
                        ? "Run only when you intend to prove paid provider access."
                        : "Keep deterministic demo mode or configure provider credentials before paid smoke tests.",
                false
        );
    }

    private PrivateDemoLaunchRehearsalStepVo backupDryRun(Map<String, String> sectionStatuses) {
        String status = storageStatus(sectionStatuses);
        return step(
                "backup-dry-run",
                "Backup dry-run",
                status,
                "Validates backup command shape without exporting service data.",
                "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-backup.sh --dry-run",
                "/tmp/linguaframe-private-demo-backups",
                status.equals(READY)
                        ? "Run before a hosted demo to verify recovery preparation."
                        : "Fix storage and recovery readiness before relying on backup commands.",
                true
        );
    }

    private PrivateDemoLaunchRehearsalStepVo restoreDryRun(Map<String, String> sectionStatuses) {
        String status = storageStatus(sectionStatuses);
        return step(
                "restore-dry-run",
                "Restore dry-run",
                status,
                "Validates restore command shape against an explicit backup directory.",
                "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-restore.sh --dry-run --backup-dir <backup-dir>",
                "/tmp/linguaframe-private-demo-backups/<timestamp>.linguaframe-backup",
                status.equals(READY)
                        ? "Run only against a known backup directory after backup dry-run passes."
                        : "Fix storage and recovery readiness before restore validation.",
                true
        );
    }

    private PrivateDemoLaunchRehearsalStepVo shortSmokeDemo(Map<String, String> sectionStatuses) {
        String status = demoEvidenceStatus(sectionStatuses);
        return step(
                "short-smoke-demo",
                "Short deterministic smoke demo",
                status,
                "Runs the local deterministic E2E path and writes compact evidence.",
                "scripts/demo/docker-e2e-success.sh",
                "/tmp/linguaframe-demo",
                "Use this as the first upload proof before full-video or provider-backed runs.",
                false
        );
    }

    private PrivateDemoLaunchRehearsalStepVo fullTearsDemo(Map<String, String> sectionStatuses) {
        String status = BLOCKED.equals(sectionStatuses.getOrDefault("Live dependencies", READY)) ? BLOCKED : ATTENTION;
        return step(
                "full-tears-demo",
                "Full Tears of Steel demo",
                status,
                "Processes the complete public demo sample under the configured duration limit.",
                "scripts/demo/docker-e2e-tears-of-steel-full.sh",
                "/tmp/linguaframe-demo/tears-of-steel-full",
                "Run after the short smoke path passes and provider/burn-in settings are intentional.",
                false
        );
    }

    private PrivateDemoLaunchRehearsalStepVo presenterPackExport(Map<String, String> sectionStatuses) {
        String status = demoEvidenceStatus(sectionStatuses);
        return step(
                "presenter-pack-export",
                "Presenter pack export",
                status,
                "Exports selected-job readiness, recommended runs, notes, and safe evidence links.",
                "Fetch /api/jobs/{jobId}/demo-presenter-pack after a completed demo job.",
                "/api/jobs/{jobId}/demo-presenter-pack",
                "Use a completed job from the same demo source before handing evidence to a reviewer.",
                false
        );
    }

    private PrivateDemoLaunchRehearsalStepVo operationsReportExport(PrivateDemoOperationsVo operations) {
        String status = BLOCKED.equals(operations.overallStatus()) ? BLOCKED : READY;
        return step(
                "operations-report-export",
                "Operations report export",
                status,
                "Writes a metadata-only operations readiness report for handoff.",
                "LINGUAFRAME_ENV_FILE=.env.private-demo scripts/demo/private-demo-operations-report.sh",
                "/tmp/linguaframe-demo/private-demo-operations-report.md",
                status.equals(READY)
                        ? "Attach this report to the launch rehearsal notes."
                        : "Fix blocked operations readiness sections before exporting the launch packet.",
                true
        );
    }

    private String storageStatus(Map<String, String> sectionStatuses) {
        return switch (sectionStatuses.getOrDefault("Storage and recovery", READY)) {
            case BLOCKED -> BLOCKED;
            case ATTENTION -> ATTENTION;
            default -> READY;
        };
    }

    private String demoEvidenceStatus(Map<String, String> sectionStatuses) {
        return switch (sectionStatuses.getOrDefault("Demo evidence", ATTENTION)) {
            case BLOCKED -> BLOCKED;
            case READY -> READY;
            default -> ATTENTION;
        };
    }

    private PrivateDemoLaunchRehearsalStepVo step(
            String id,
            String title,
            String status,
            String detail,
            String command,
            String evidencePath,
            String nextAction,
            boolean blocking
    ) {
        return new PrivateDemoLaunchRehearsalStepVo(
                id,
                title,
                status,
                detail,
                command,
                evidencePath,
                nextAction,
                blocking && BLOCKED.equals(status)
        );
    }

    private String notes(
            String overallStatus,
            String recommendedNextStepId,
            List<PrivateDemoLaunchRehearsalStepVo> steps,
            List<String> evidenceDownloads
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Private Demo Launch Rehearsal");
        lines.add("");
        lines.add("- Overall: " + overallStatus);
        lines.add("- Recommended next step: " + recommendedNextStepId);
        lines.add("- Terminal report: scripts/demo/private-demo-launch-rehearsal.sh");
        lines.add("");
        lines.add("## Steps");
        for (PrivateDemoLaunchRehearsalStepVo step : steps) {
            lines.add("- " + step.status() + " " + step.id() + ": " + step.title());
            lines.add("  Command: " + step.command());
            lines.add("  Evidence: " + step.evidencePath());
            lines.add("  Next: " + step.nextAction());
        }
        lines.add("");
        lines.add("## Evidence Routes");
        for (String route : evidenceDownloads) {
            lines.add("- " + route);
        }
        return String.join("\n", lines);
    }

    private static int severity(String status) {
        return switch (status) {
            case BLOCKED -> 0;
            case ATTENTION -> 1;
            default -> 2;
        };
    }

    private static String worst(String first, String... rest) {
        String result = first;
        for (String status : rest) {
            if (rank(status) > rank(result)) {
                result = status;
            }
        }
        return result;
    }

    private static int rank(String status) {
        return switch (status) {
            case BLOCKED -> 2;
            case ATTENTION -> 1;
            default -> 0;
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
