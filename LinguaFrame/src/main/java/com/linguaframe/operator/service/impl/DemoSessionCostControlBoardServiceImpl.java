package com.linguaframe.operator.service.impl;

import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.common.runtime.domain.vo.BudgetReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlActionVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlBoardVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlBudgetVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlCheckVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlJobVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlLinkVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlOperationVo;
import com.linguaframe.operator.domain.vo.DemoSessionCostControlSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerJobVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerOperationVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.service.DemoSessionCostControlBoardService;
import com.linguaframe.operator.service.ModelUsageLedgerService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class DemoSessionCostControlBoardServiceImpl implements DemoSessionCostControlBoardService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;
    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String EMPTY = "EMPTY";

    private final ModelUsageLedgerService modelUsageLedgerService;
    private final RuntimeDependencySummaryService runtimeDependencySummaryService;
    private final OwnerQuotaPreflightService ownerQuotaPreflightService;

    public DemoSessionCostControlBoardServiceImpl(
            ModelUsageLedgerService modelUsageLedgerService,
            RuntimeDependencySummaryService runtimeDependencySummaryService,
            OwnerQuotaPreflightService ownerQuotaPreflightService
    ) {
        this.modelUsageLedgerService = modelUsageLedgerService;
        this.runtimeDependencySummaryService = runtimeDependencySummaryService;
        this.ownerQuotaPreflightService = ownerQuotaPreflightService;
    }

    @Override
    public DemoSessionCostControlBoardVo board(Integer limit) {
        int normalizedLimit = normalizeLimit(limit);
        ModelUsageLedgerVo ledger = modelUsageLedgerService.ledger(normalizedLimit);
        RuntimeDependencySummaryVo runtime = runtimeDependencySummaryService.getSummary();
        OwnerQuotaPreflightVo quota = ownerQuotaPreflightService.getPreflight();
        BudgetReadinessVo budget = runtime.readiness().budget();
        BigDecimal recentCost = safeCost(ledger.summary().estimatedCostUsd());
        BigDecimal dailyCost = safeCost(quota.dailyEstimatedCostUsd());
        List<DemoSessionCostControlBudgetVo> budgets = budgets(budget, quota, recentCost, dailyCost);
        List<DemoSessionCostControlCheckVo> checks = checks(ledger, budget, quota, recentCost, dailyCost);
        String status = overallStatus(ledger, checks);
        DemoSessionCostControlSummaryVo summary = new DemoSessionCostControlSummaryVo(
                ledger.ownerId(),
                ledger.ownershipScope(),
                budget.estimatedCostTrackingEnabled(),
                recentCost,
                dailyCost,
                quota.dailyBudgetDate(),
                ledger.summary().modelCallCount(),
                ledger.summary().failedModelCallCount(),
                ledger.summary().failureRatePercent(),
                recommendedNextAction(status, ledger, budget, quota)
        );
        DemoSessionCostControlBoardVo board = new DemoSessionCostControlBoardVo(
                Instant.now(),
                status,
                summary,
                budgets,
                jobs(ledger),
                operations(ledger),
                checks,
                primaryAction(status),
                links(),
                safetyNotes(),
                ""
        );
        return new DemoSessionCostControlBoardVo(
                board.generatedAt(),
                board.overallStatus(),
                board.summary(),
                board.budgets(),
                board.jobs(),
                board.operations(),
                board.checks(),
                board.primaryAction(),
                board.links(),
                board.safetyNotes(),
                markdown(board)
        );
    }

    @Override
    public String boardMarkdown(Integer limit) {
        return board(limit).markdown();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<DemoSessionCostControlBudgetVo> budgets(
            BudgetReadinessVo budget,
            OwnerQuotaPreflightVo quota,
            BigDecimal recentCost,
            BigDecimal dailyCost
    ) {
        List<DemoSessionCostControlBudgetVo> budgets = new ArrayList<>();
        budgets.add(new DemoSessionCostControlBudgetVo(
                "perJobBudget",
                "Per-job budget guard",
                perJobBudgetStatus(budget, recentCost),
                budget.enabled(),
                safeCost(budget.maxJobCostUsd()),
                recentCost,
                budget.enabled()
                        ? "Per-job budget guard is enabled for local estimated cost."
                        : "Per-job budget guard is disabled.",
                budget.enabled() && overLimit(recentCost, budget.maxJobCostUsd())
        ));
        budgets.add(new DemoSessionCostControlBudgetVo(
                "dailyBudget",
                "Daily budget guard",
                dailyBudgetStatus(budget, dailyCost),
                budget.dailyBudgetGuardEnabled(),
                safeCost(budget.maxDailyCostUsd()),
                dailyCost,
                budget.dailyBudgetGuardEnabled()
                        ? "Daily budget guard is enabled for " + safeText(budget.budgetIdentity()) + "."
                        : "Daily budget guard is disabled.",
                budget.dailyBudgetGuardEnabled() && overLimit(dailyCost, budget.maxDailyCostUsd())
        ));
        OwnerQuotaLimitVo dailyQuota = quota.limits().stream()
                .filter(limit -> "dailyCostUsd".equals(limit.name()))
                .findFirst()
                .orElse(null);
        budgets.add(new DemoSessionCostControlBudgetVo(
                "ownerDailyQuota",
                "Owner daily quota",
                quota.allowed() ? READY : BLOCKED,
                quota.enabled(),
                dailyQuota == null ? BigDecimal.ZERO : safeCost(dailyQuota.limit()),
                dailyCost,
                quota.allowed()
                        ? "Owner quota allows another upload decision."
                        : "Owner quota currently blocks additional upload decisions.",
                !quota.allowed()
        ));
        return List.copyOf(budgets);
    }

    private List<DemoSessionCostControlCheckVo> checks(
            ModelUsageLedgerVo ledger,
            BudgetReadinessVo budget,
            OwnerQuotaPreflightVo quota,
            BigDecimal recentCost,
            BigDecimal dailyCost
    ) {
        List<DemoSessionCostControlCheckVo> checks = new ArrayList<>();
        checks.add(new DemoSessionCostControlCheckVo(
                "costTracking",
                "Estimated cost tracking",
                budget.estimatedCostTrackingEnabled() ? READY : ATTENTION,
                budget.estimatedCostTrackingEnabled()
                        ? "Local estimated cost tracking is enabled."
                        : "Local estimated cost tracking is disabled, so spend evidence may be incomplete.",
                budget.estimatedCostTrackingEnabled()
                        ? "Use the board as local spend evidence."
                        : "Enable cost tracking before treating local estimates as demo evidence.",
                false
        ));
        checks.add(new DemoSessionCostControlCheckVo(
                "perJobBudgetGuard",
                "Per-job budget guard",
                perJobBudgetStatus(budget, recentCost),
                budget.enabled()
                        ? "Per-job budget guard limit is " + safeCost(budget.maxJobCostUsd()) + "."
                        : "Per-job budget guard is disabled.",
                budget.enabled()
                        ? "Keep the guard enabled for paid demo runs."
                        : "Review budget guard settings before another paid run.",
                budget.enabled() && overLimit(recentCost, budget.maxJobCostUsd())
        ));
        checks.add(new DemoSessionCostControlCheckVo(
                "dailyBudgetGuard",
                "Daily budget guard",
                dailyBudgetStatus(budget, dailyCost),
                budget.dailyBudgetGuardEnabled()
                        ? "Daily budget guard limit is " + safeCost(budget.maxDailyCostUsd()) + "."
                        : "Daily budget guard is disabled.",
                budget.dailyBudgetGuardEnabled()
                        ? "Watch owner daily spend before another provider-backed run."
                        : "Review budget guard settings before another paid run.",
                budget.dailyBudgetGuardEnabled() && overLimit(dailyCost, budget.maxDailyCostUsd())
        ));
        checks.add(new DemoSessionCostControlCheckVo(
                "ownerQuota",
                "Owner quota preflight",
                quota.allowed() ? READY : BLOCKED,
                quota.allowed()
                        ? "Owner quota currently allows upload decisions."
                        : safeBlockingReason(quota),
                quota.allowed()
                        ? "Use upload readiness before starting another run."
                        : "Stop paid demo runs until quota blocking reasons are cleared.",
                !quota.allowed()
        ));
        checks.add(new DemoSessionCostControlCheckVo(
                "modelUsageFailures",
                "Recent model-call failures",
                ledger.summary().ledgerStatus(),
                ledger.summary().failedModelCallCount() + " failed calls across " + ledger.summary().modelCallCount() + " recent calls.",
                ledger.summary().recommendedNextAction(),
                BLOCKED.equals(ledger.summary().ledgerStatus())
        ));
        return List.copyOf(checks);
    }

    private String perJobBudgetStatus(BudgetReadinessVo budget, BigDecimal recentCost) {
        if (budget.enabled() && overLimit(recentCost, budget.maxJobCostUsd())) {
            return BLOCKED;
        }
        if (!budget.enabled()) {
            return ATTENTION;
        }
        return READY;
    }

    private String dailyBudgetStatus(BudgetReadinessVo budget, BigDecimal dailyCost) {
        if (budget.dailyBudgetGuardEnabled() && overLimit(dailyCost, budget.maxDailyCostUsd())) {
            return BLOCKED;
        }
        if (!budget.dailyBudgetGuardEnabled()) {
            return ATTENTION;
        }
        return READY;
    }

    private boolean overLimit(BigDecimal current, BigDecimal limit) {
        return limit != null && limit.compareTo(BigDecimal.ZERO) > 0 && current.compareTo(limit) >= 0;
    }

    private String overallStatus(ModelUsageLedgerVo ledger, List<DemoSessionCostControlCheckVo> checks) {
        if (checks.stream().anyMatch(DemoSessionCostControlCheckVo::blocking)) {
            return BLOCKED;
        }
        if (BLOCKED.equals(ledger.summary().ledgerStatus())) {
            return BLOCKED;
        }
        if (checks.stream().anyMatch(check -> ATTENTION.equals(check.status()))) {
            return ATTENTION;
        }
        if (EMPTY.equals(ledger.summary().ledgerStatus())) {
            return EMPTY;
        }
        if (ATTENTION.equals(ledger.summary().ledgerStatus())) {
            return ATTENTION;
        }
        return READY;
    }

    private String recommendedNextAction(
            String status,
            ModelUsageLedgerVo ledger,
            BudgetReadinessVo budget,
            OwnerQuotaPreflightVo quota
    ) {
        if (BLOCKED.equals(status)) {
            return "Stop paid demo runs and resolve budget, quota, or model-call blockers before uploading again.";
        }
        if (EMPTY.equals(status)) {
            return "Run a demo job, then refresh cost control before presenting spend evidence.";
        }
        if (!budget.enabled() || !budget.dailyBudgetGuardEnabled()) {
            return "Review budget guard settings before another paid demo run.";
        }
        if (ledger.summary().failedModelCallCount() > 0 || !quota.allowed()) {
            return "Review model-call failures and owner quota evidence before another paid run.";
        }
        return "Use this board as local estimated cost evidence before running or presenting the demo.";
    }

    private DemoSessionCostControlActionVo primaryAction(String status) {
        if (BLOCKED.equals(status)) {
            return new DemoSessionCostControlActionVo(
                    "OPEN_UPLOAD_READINESS",
                    "Open upload readiness",
                    "/api/media/uploads/readiness",
                    "Inspect upload and budget gates before another paid run.",
                    true
            );
        }
        return new DemoSessionCostControlActionVo(
                "OPEN_MODEL_USAGE_LEDGER",
                "Open model usage ledger",
                "/api/operator/model-usage-ledger",
                "Inspect recent model-call cost, latency, and failure evidence.",
                true
        );
    }

    private List<DemoSessionCostControlJobVo> jobs(ModelUsageLedgerVo ledger) {
        return ledger.jobs().stream()
                .sorted(Comparator.comparing(ModelUsageLedgerJobVo::estimatedCostUsd).reversed())
                .map(job -> new DemoSessionCostControlJobVo(
                        job.jobId(),
                        job.videoId(),
                        job.jobStatus(),
                        job.targetLanguage(),
                        job.demoProfileId(),
                        job.modelCallCount(),
                        job.failedModelCallCount(),
                        safeCost(job.estimatedCostUsd()),
                        job.latestModelCallAt(),
                        job.safeLinks()
                ))
                .toList();
    }

    private List<DemoSessionCostControlOperationVo> operations(ModelUsageLedgerVo ledger) {
        return ledger.operations().stream()
                .sorted(Comparator.comparing(ModelUsageLedgerOperationVo::estimatedCostUsd).reversed())
                .map(operation -> new DemoSessionCostControlOperationVo(
                        operation.operation(),
                        operation.provider(),
                        operation.model(),
                        operation.promptVersion(),
                        operation.modelCallCount(),
                        operation.failedModelCallCount(),
                        safeCost(operation.estimatedCostUsd()),
                        operation.averageLatencyMs()
                ))
                .toList();
    }

    private List<DemoSessionCostControlLinkVo> links() {
        return List.of(
                new DemoSessionCostControlLinkVo(
                        "JSON",
                        "Demo session cost control board",
                        "/api/operator/demo-session-cost-control-board",
                        "application/json",
                        "Safe session-level cost control board JSON."
                ),
                new DemoSessionCostControlLinkVo(
                        "MARKDOWN",
                        "Demo session cost control Markdown",
                        "/api/operator/demo-session-cost-control-board/markdown/download",
                        "text/markdown",
                        "Downloadable cost control board report."
                ),
                new DemoSessionCostControlLinkVo(
                        "LEDGER",
                        "Model usage ledger",
                        "/api/operator/model-usage-ledger",
                        "application/json",
                        "Recent model-call usage, cost, latency, and failure evidence."
                )
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Estimated costs are local estimates and provider billing remains the source of truth.",
                "This board is read-only and does not upload media, call providers, run FFmpeg, or mutate budget settings.",
                "API keys, tokens, prompts, provider payloads, object keys, local media paths, transcripts, subtitles, and media bytes are intentionally excluded."
        );
    }

    private String markdown(DemoSessionCostControlBoardVo board) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# LinguaFrame Demo Session Cost Control Board\n\n");
        markdown.append("- Status: ").append(board.overallStatus()).append('\n');
        markdown.append("- Owner: ").append(board.summary().ownerId()).append('\n');
        markdown.append("- Recent estimated cost USD: ").append(board.summary().recentEstimatedCostUsd()).append('\n');
        markdown.append("- Daily estimated cost USD: ").append(board.summary().dailyEstimatedCostUsd()).append('\n');
        markdown.append("- Model calls: ").append(board.summary().modelCallCount()).append('\n');
        markdown.append("- Failed model calls: ").append(board.summary().failedModelCallCount()).append('\n');
        markdown.append("- Failure rate: ").append(board.summary().failureRatePercent()).append("%\n");
        markdown.append("- Next action: ").append(board.summary().recommendedNextAction()).append("\n\n");
        markdown.append("## Budgets\n\n");
        for (DemoSessionCostControlBudgetVo budget : board.budgets()) {
            markdown.append("- ").append(budget.status()).append(" ").append(budget.label())
                    .append(": current=").append(budget.currentUsd())
                    .append(" limit=").append(budget.limitUsd())
                    .append(" enabled=").append(budget.enabled()).append('\n');
        }
        markdown.append("\n## Checks\n\n");
        for (DemoSessionCostControlCheckVo check : board.checks()) {
            markdown.append("- ").append(check.status()).append(" ").append(check.label())
                    .append(": ").append(check.detail()).append('\n');
        }
        markdown.append("\n## Jobs\n\n");
        if (board.jobs().isEmpty()) {
            markdown.append("- No recent model-call job cost evidence is available.\n");
        } else {
            for (DemoSessionCostControlJobVo job : board.jobs()) {
                markdown.append("- `").append(job.jobId()).append("` ")
                        .append(job.jobStatus()).append(" calls=").append(job.modelCallCount())
                        .append(" failed=").append(job.failedModelCallCount())
                        .append(" cost=").append(job.estimatedCostUsd()).append('\n');
            }
        }
        markdown.append("\n## Safety Notes\n\n");
        board.safetyNotes().forEach(note -> markdown.append("- ").append(note).append('\n'));
        return markdown.toString();
    }

    private String safeBlockingReason(OwnerQuotaPreflightVo quota) {
        if (quota.blockingReasons().isEmpty()) {
            return "Owner quota blocks upload decisions.";
        }
        return quota.blockingReasons().getFirst()
                .replaceAll("(/[A-Za-z0-9._ -]+)+", "[redacted-path]");
    }

    private BigDecimal safeCost(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String safeText(String value) {
        return value == null || value.isBlank() ? "configured owner" : value;
    }
}
