package com.linguaframe.operator.service.impl;

import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.DemoReadinessVo;
import com.linguaframe.common.runtime.domain.vo.ProviderReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeFeatureFlagVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.service.RetentionCleanupService;
import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.domain.vo.OperatorJobStatusCountVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCheckVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsCommandVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsLinkVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsSectionVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.service.OperatorDashboardService;
import com.linguaframe.operator.service.PrivateDemoOperationsService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class PrivateDemoOperationsServiceImpl implements PrivateDemoOperationsService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String OPERATIONS_ROUTE = "/api/operator/private-demo/operations";

    private final RuntimeDependencySummaryService summaryService;
    private final RuntimeLiveCheckService liveCheckService;
    private final OperatorDashboardService dashboardService;
    private final RetentionCleanupService retentionCleanupService;

    public PrivateDemoOperationsServiceImpl(
            RuntimeDependencySummaryService summaryService,
            RuntimeLiveCheckService liveCheckService,
            OperatorDashboardService dashboardService,
            RetentionCleanupService retentionCleanupService
    ) {
        this.summaryService = summaryService;
        this.liveCheckService = liveCheckService;
        this.dashboardService = dashboardService;
        this.retentionCleanupService = retentionCleanupService;
    }

    @Override
    public PrivateDemoOperationsVo operations() {
        RuntimeDependencySummaryVo summary = summaryService.getSummary();
        RuntimeLiveCheckSummaryVo liveChecks = liveCheckService.check();
        OperatorDashboardVo dashboard = dashboardService.dashboard();
        RetentionCleanupResultVo cleanupPreview = retentionCleanupService.previewCleanup();

        List<PrivateDemoOperationsSectionVo> sections = List.of(
                accessGate(summary.readiness()),
                runtimeContract(summary),
                liveDependencies(liveChecks),
                providerReadiness(summary.readiness()),
                costSafety(summary.readiness()),
                storageAndRecovery(summary),
                retentionCleanup(summary.readiness(), cleanupPreview),
                demoEvidence(dashboard)
        );

        List<PrivateDemoOperationsCheckVo> checks = sections.stream()
                .flatMap(section -> section.checks().stream())
                .toList();
        long readyCount = checks.stream().filter(check -> READY.equals(check.status())).count();
        long attentionCount = checks.stream().filter(check -> ATTENTION.equals(check.status())).count();
        long blockedCount = checks.stream().filter(check -> BLOCKED.equals(check.status())).count();
        String overallStatus = blockedCount > 0 ? BLOCKED : attentionCount > 0 ? ATTENTION : READY;

        return new PrivateDemoOperationsVo(
                Instant.now(),
                overallStatus,
                readyCount,
                attentionCount,
                blockedCount,
                sections,
                commands(),
                documentationLinks()
        );
    }

    private PrivateDemoOperationsSectionVo accessGate(DemoReadinessVo readiness) {
        return section("Access gate", List.of(
                check(
                        "Owner access gate",
                        readiness.demoAccessGate() ? READY : ATTENTION,
                        readiness.demoAccessGate()
                                ? "Private demo API access requires the configured owner token or browser owner session."
                                : "Private demo API access is open because no owner token is configured.",
                        readiness.demoAccessGate()
                                ? "Use the browser owner-session login or demo token header for API calls."
                                : "Set LINGUAFRAME_DEMO_ACCESS_TOKEN before exposing a private demo URL."
                )
        ));
    }

    private PrivateDemoOperationsSectionVo runtimeContract(RuntimeDependencySummaryVo summary) {
        boolean hasRoute = summary.runtime().requiredRoutes().contains(OPERATIONS_ROUTE);
        boolean hasMigration = summary.runtime().latestMigrationVersion() > 0;
        return section("Runtime contract", List.of(
                check(
                        "Backend route contract",
                        hasRoute ? READY : BLOCKED,
                        hasRoute
                                ? OPERATIONS_ROUTE + " is listed in the backend runtime contract."
                                : OPERATIONS_ROUTE + " is missing from the runtime contract.",
                        hasRoute
                                ? "Run private-demo preflight before uploads to confirm the deployed backend is fresh."
                                : "Rebuild and recreate the backend container, then rerun preflight."
                ),
                check(
                        "Bundled migration contract",
                        hasMigration ? READY : BLOCKED,
                        hasMigration
                                ? "Latest bundled migration is V" + summary.runtime().latestMigrationVersion() + "."
                                : "No bundled Flyway migration version was detected.",
                        hasMigration
                                ? "Keep the deployed backend image aligned with the repository build."
                                : "Inspect db/migration packaging before deploying the private demo."
                )
        ));
    }

    private PrivateDemoOperationsSectionVo liveDependencies(RuntimeLiveCheckSummaryVo liveChecks) {
        List<PrivateDemoOperationsCheckVo> checks = new ArrayList<>();
        liveChecks.checks().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> checks.add(liveDependency(entry.getKey(), entry.getValue())));
        return section("Live dependencies", checks);
    }

    private PrivateDemoOperationsCheckVo liveDependency(String name, RuntimeProbeResultVo probe) {
        String status = switch (probe.status()) {
            case UP -> READY;
            case DOWN -> BLOCKED;
            case SKIPPED -> ATTENTION;
        };
        String nextAction = switch (probe.status()) {
            case UP -> "No action required before the next demo run.";
            case DOWN -> "Fix " + name + " connectivity before uploading media.";
            case SKIPPED -> "Enable the check when this dependency must be proven before a provider-backed demo.";
        };
        return check(name, status, probe.message(), nextAction);
    }

    private PrivateDemoOperationsSectionVo providerReadiness(DemoReadinessVo readiness) {
        List<PrivateDemoOperationsCheckVo> checks = readiness.providers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> provider(entry.getKey(), entry.getValue()))
                .toList();
        return section("Provider readiness", checks);
    }

    private PrivateDemoOperationsCheckVo provider(String name, ProviderReadinessVo provider) {
        boolean demoProvider = "demo".equalsIgnoreCase(provider.provider());
        boolean ready = !provider.enabled() || demoProvider || provider.credentialsConfigured();
        String detail = provider.enabled()
                ? name + " uses " + provider.provider() + " / " + safeModel(provider.model()) + "."
                : name + " provider stage is disabled.";
        return check(
                name,
                ready ? READY : BLOCKED,
                detail,
                ready
                        ? "Run OpenAI preflight before paid provider-backed media uploads."
                        : "Configure provider credentials on the server before running this stage."
        );
    }

    private PrivateDemoOperationsSectionVo costSafety(DemoReadinessVo readiness) {
        return section("Cost safety", List.of(
                check(
                        "Per-job budget guard",
                        readiness.budget().enabled() ? READY : ATTENTION,
                        readiness.budget().enabled()
                                ? "Per-job budget guard is enabled at $" + readiness.budget().maxJobCostUsd() + "."
                                : "Per-job budget guard is disabled.",
                        readiness.budget().enabled()
                                ? "Keep the limit aligned with the planned demo sample length."
                                : "Enable the budget guard before provider-backed private demos."
                ),
                check(
                        "Daily demo budget",
                        readiness.budget().dailyBudgetGuardEnabled() ? READY : ATTENTION,
                        readiness.budget().dailyBudgetGuardEnabled()
                                ? "Daily budget guard is enabled for " + readiness.budget().budgetIdentity() + "."
                                : "Daily budget guard is disabled.",
                        readiness.budget().dailyBudgetGuardEnabled()
                                ? "Use a safe configured identity, not raw tokens or IP addresses."
                                : "Enable the daily guard when repeated demo uploads are expected."
                )
        ));
    }

    private PrivateDemoOperationsSectionVo storageAndRecovery(RuntimeDependencySummaryVo summary) {
        return section("Storage and recovery", List.of(
                check(
                        "Object storage",
                        hasText(summary.storage().bucket()) ? READY : BLOCKED,
                        hasText(summary.storage().bucket())
                                ? "Object storage bucket is configured."
                                : "Object storage bucket is missing.",
                        "Run private-demo backup dry-run before the demo and store backups outside the repository."
                ),
                check(
                        "Backup and restore commands",
                        READY,
                        "Dry-run backup and restore commands are available for operator validation.",
                        "Run backup dry-run first; run restore dry-run only against an explicit backup directory."
                )
        ));
    }

    private PrivateDemoOperationsSectionVo retentionCleanup(
            DemoReadinessVo readiness,
            RetentionCleanupResultVo cleanupPreview
    ) {
        Optional<RuntimeFeatureFlagVo> retentionFlag = Optional.ofNullable(readiness.features().get("retentionCleanup"));
        boolean enabled = retentionFlag.map(RuntimeFeatureFlagVo::enabled).orElse(false);
        return section("Retention cleanup", List.of(
                check(
                        "Retention policy",
                        enabled ? READY : ATTENTION,
                        enabled
                                ? "Retention cleanup is enabled and preview reports "
                                + cleanupPreview.candidateJobCount() + " candidate jobs."
                                : "Retention cleanup is disabled.",
                        enabled
                                ? "Review the browser preview before any deleting cleanup run."
                                : "Enable retention cleanup when the private demo should prune terminal jobs."
                ),
                check(
                        "Retention dry-run preview",
                        cleanupPreview.dryRun() ? READY : ATTENTION,
                        cleanupPreview.dryRun()
                                ? "Cleanup preview is dry-run only."
                                : "Cleanup preview reflects delete mode.",
                        "Keep dry-run enabled until the operator explicitly chooses a deleting cleanup run."
                )
        ));
    }

    private PrivateDemoOperationsSectionVo demoEvidence(OperatorDashboardVo dashboard) {
        long jobCount = dashboard.statusCounts().stream()
                .mapToLong(OperatorJobStatusCountVo::count)
                .sum();
        return section("Demo evidence", List.of(
                check(
                        "Recorded demo jobs",
                        jobCount > 0 ? READY : ATTENTION,
                        jobCount > 0
                                ? jobCount + " jobs are visible in the operator dashboard."
                                : "No demo jobs have been recorded yet.",
                        jobCount > 0
                                ? "Use a completed job for browser review, evidence, and handoff export."
                                : "Run the deterministic Docker E2E script or upload a safe sample video."
                ),
                check(
                        "Model-call failures",
                        dashboard.modelCalls().failedModelCallCount() == 0 ? READY : ATTENTION,
                        dashboard.modelCalls().failedModelCallCount() == 0
                                ? "No failed model calls are recorded in the operator summary."
                                : dashboard.modelCalls().failedModelCallCount() + " failed model calls are recorded.",
                        "Open the failed job and review failure triage before presenting the demo."
                )
        ));
    }

    private PrivateDemoOperationsSectionVo section(String title, List<PrivateDemoOperationsCheckVo> checks) {
        String status = checks.stream().map(PrivateDemoOperationsCheckVo::status)
                .max(Comparator.comparingInt(PrivateDemoOperationsServiceImpl::severity))
                .orElse(READY);
        return new PrivateDemoOperationsSectionVo(title, status, checks);
    }

    private static int severity(String status) {
        return switch (status) {
            case BLOCKED -> 2;
            case ATTENTION -> 1;
            default -> 0;
        };
    }

    private PrivateDemoOperationsCheckVo check(String label, String status, String detail, String nextAction) {
        return new PrivateDemoOperationsCheckVo(label, status, detail, nextAction);
    }

    private List<PrivateDemoOperationsCommandVo> commands() {
        return List.of(
                new PrivateDemoOperationsCommandVo(
                        "Private demo preflight",
                        "scripts/demo/private-demo-preflight.sh",
                        "Checks local env, backend freshness, browser reachability, live dependencies, owner session, and sample paths."
                ),
                new PrivateDemoOperationsCommandVo(
                        "OpenAI preflight",
                        "scripts/demo/openai-demo-preflight.sh",
                        "Verifies provider-backed configuration before paid demo uploads."
                ),
                new PrivateDemoOperationsCommandVo(
                        "Deterministic E2E",
                        "scripts/demo/docker-e2e-success.sh",
                        "Runs the safe Docker success path and writes demo evidence files."
                ),
                new PrivateDemoOperationsCommandVo(
                        "Backup dry-run",
                        "scripts/demo/private-demo-backup.sh --dry-run",
                        "Validates backup shape without exporting service data."
                ),
                new PrivateDemoOperationsCommandVo(
                        "Restore dry-run",
                        "scripts/demo/private-demo-restore.sh --dry-run --backup-dir <backup-dir>",
                        "Validates a selected backup before any guarded restore."
                )
        );
    }

    private List<PrivateDemoOperationsLinkVo> documentationLinks() {
        return List.of(
                new PrivateDemoOperationsLinkVo(
                        "Private demo deployment",
                        "docs/deployment/private-demo.md",
                        "Reverse proxy, env, backup, and restore runbook."
                ),
                new PrivateDemoOperationsLinkVo(
                        "Smoke test checklist",
                        "docs/agent/smoke-test-checklist.md",
                        "Validation commands and expected demo evidence."
                ),
                new PrivateDemoOperationsLinkVo(
                        "Docker E2E demo",
                        "docs/agent/docker-e2e-demo.md",
                        "Local Docker demo workflow and troubleshooting."
                )
        );
    }

    private static String safeModel(String model) {
        return hasText(model) ? model : "unconfigured";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
