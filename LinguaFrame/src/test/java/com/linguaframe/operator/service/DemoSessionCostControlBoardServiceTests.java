package com.linguaframe.operator.service;

import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
import com.linguaframe.common.runtime.domain.vo.BudgetReadinessVo;
import com.linguaframe.common.runtime.domain.vo.DemoReadinessVo;
import com.linguaframe.common.runtime.domain.vo.OwnerQuotaReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerJobVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerOperationVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.service.impl.DemoSessionCostControlBoardServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSessionCostControlBoardServiceTests {

    private final StubModelUsageLedgerService modelUsageLedgerService = new StubModelUsageLedgerService();
    private final StubRuntimeDependencySummaryService runtimeSummaryService = new StubRuntimeDependencySummaryService();
    private final StubOwnerQuotaPreflightService ownerQuotaPreflightService = new StubOwnerQuotaPreflightService();
    private final DemoSessionCostControlBoardService service = new DemoSessionCostControlBoardServiceImpl(
            modelUsageLedgerService,
            runtimeSummaryService,
            ownerQuotaPreflightService
    );

    @Test
    void buildsEmptyBoardWhenCostTrackingIsEnabledButNoModelCallsExist() {
        modelUsageLedgerService.ledger = ledger("EMPTY", 0, 0, "0.00000000", "0.00");
        runtimeSummaryService.budget = budget(true, "0.50000000", true, "2.00000000", true);
        ownerQuotaPreflightService.preflight = quota(true, true, "0.00000000", List.of());

        var board = service.board(25);

        assertThat(board.overallStatus()).isEqualTo("EMPTY");
        assertThat(board.summary().recentEstimatedCostUsd()).isEqualByComparingTo("0.00000000");
        assertThat(board.summary().dailyEstimatedCostUsd()).isEqualByComparingTo("0.00000000");
        assertThat(board.summary().modelCallCount()).isZero();
        assertThat(board.summary().recommendedNextAction()).contains("Run a demo job");
        assertThat(board.budgets()).extracting("key").contains("perJobBudget", "dailyBudget", "ownerDailyQuota");
        assertThat(board.links()).extracting("href").contains("/api/operator/demo-session-cost-control-board/markdown/download");
    }

    @Test
    void marksReadyWhenBudgetsAreEnabledAndRecentUsageIsHealthy() {
        modelUsageLedgerService.ledger = ledger("READY", 4, 0, "0.12000000", "0.00");
        modelUsageLedgerService.ledger = withJobsAndOperations(modelUsageLedgerService.ledger);
        runtimeSummaryService.budget = budget(true, "0.50000000", true, "2.00000000", true);
        ownerQuotaPreflightService.preflight = quota(true, true, "0.25000000", List.of());

        var board = service.board(25);

        assertThat(board.overallStatus()).isEqualTo("READY");
        assertThat(board.summary().recentEstimatedCostUsd()).isEqualByComparingTo("0.12000000");
        assertThat(board.summary().dailyEstimatedCostUsd()).isEqualByComparingTo("0.25000000");
        assertThat(board.jobs()).singleElement().satisfies(job -> {
            assertThat(job.jobId()).isEqualTo("cost-job");
            assertThat(job.estimatedCostUsd()).isEqualByComparingTo("0.12000000");
            assertThat(job.safeLinks()).contains("/api/jobs/cost-job/ai-audit-package/download");
        });
        assertThat(board.operations()).singleElement().satisfies(operation -> {
            assertThat(operation.operation()).isEqualTo("TRANSLATION");
            assertThat(operation.estimatedCostUsd()).isEqualByComparingTo("0.12000000");
        });
        assertThat(board.primaryAction().key()).isEqualTo("OPEN_MODEL_USAGE_LEDGER");
    }

    @Test
    void marksAttentionWhenBudgetGuardsAreDisabledForNonZeroSpend() {
        modelUsageLedgerService.ledger = ledger("READY", 2, 0, "0.04000000", "0.00");
        runtimeSummaryService.budget = budget(false, "0.00000000", false, "0.00000000", true);
        ownerQuotaPreflightService.preflight = quota(false, true, "0.04000000", List.of());

        var board = service.board(25);

        assertThat(board.overallStatus()).isEqualTo("ATTENTION");
        assertThat(board.checks()).anySatisfy(check -> {
            assertThat(check.key()).isEqualTo("perJobBudgetGuard");
            assertThat(check.status()).isEqualTo("ATTENTION");
        });
        assertThat(board.summary().recommendedNextAction()).contains("Review budget guard settings");
    }

    @Test
    void marksBlockedWhenOwnerDailyBudgetIsReached() {
        modelUsageLedgerService.ledger = ledger("READY", 5, 0, "0.60000000", "0.00");
        runtimeSummaryService.budget = budget(true, "0.50000000", true, "0.50000000", true);
        ownerQuotaPreflightService.preflight = quota(true, false, "0.60000000",
                List.of("Daily owner budget reached for owner demo-owner on 2026-07-01"));

        var board = service.board(25);

        assertThat(board.overallStatus()).isEqualTo("BLOCKED");
        assertThat(board.summary().dailyEstimatedCostUsd()).isEqualByComparingTo("0.60000000");
        assertThat(board.summary().recommendedNextAction()).contains("Stop paid demo runs");
        assertThat(board.primaryAction().key()).isEqualTo("OPEN_UPLOAD_READINESS");
    }

    @Test
    void rendersMarkdownWithoutUnsafeDetails() {
        modelUsageLedgerService.ledger = ledger("READY", 1, 0, "0.01000000", "0.00");
        runtimeSummaryService.budget = budget(true, "0.50000000", true, "2.00000000", true);
        ownerQuotaPreflightService.preflight = quota(true, true, "0.01000000",
                List.of("Unsafe local path /Users/demo/source.mp4 should not be printed"));

        String markdown = service.boardMarkdown(25);

        assertThat(markdown).contains("LinguaFrame Demo Session Cost Control Board");
        assertThat(markdown).contains("Estimated costs are local estimates");
        assertThat(markdown).doesNotContain("/Users/demo/source.mp4");
        assertThat(markdown).doesNotContain("sk-");
    }

    private ModelUsageLedgerVo ledger(
            String status,
            int calls,
            int failedCalls,
            String estimatedCost,
            String failureRate
    ) {
        ModelUsageLedgerSummaryVo summary = new ModelUsageLedgerSummaryVo(
                status,
                calls == 0 ? 0 : 1,
                calls,
                failedCalls,
                0,
                0,
                100L * calls,
                new BigDecimal(estimatedCost),
                calls == 0 ? 0 : 100L,
                new BigDecimal(failureRate),
                status.equals("EMPTY") ? "Run a demo job, then refresh this ledger before presenting model spend." : "Use the ledger links as cost evidence."
        );
        return ledger(summary, List.of(), List.of());
    }

    private BudgetReadinessVo budget(
            boolean perJobEnabled,
            String maxJob,
            boolean dailyEnabled,
            String maxDaily,
            boolean trackingEnabled
    ) {
        return new BudgetReadinessVo(
                perJobEnabled,
                new BigDecimal(maxJob),
                dailyEnabled,
                new BigDecimal(maxDaily),
                "demo-owner",
                trackingEnabled
        );
    }

    private OwnerQuotaPreflightVo quota(
            boolean enabled,
            boolean allowed,
            String dailyEstimatedCost,
            List<String> blockingReasons
    ) {
        return new OwnerQuotaPreflightVo(
                "demo-owner",
                enabled,
                allowed,
                0,
                0,
                new BigDecimal(dailyEstimatedCost),
                LocalDate.parse("2026-07-01"),
                List.of(new OwnerQuotaLimitVo("dailyCostUsd", enabled, new BigDecimal("0.50000000"), new BigDecimal(dailyEstimatedCost))),
                blockingReasons
        );
    }

    private ModelUsageLedgerVo ledger(
            ModelUsageLedgerSummaryVo summary,
            List<ModelUsageLedgerJobVo> jobs,
            List<ModelUsageLedgerOperationVo> operations
    ) {
        return new ModelUsageLedgerVo(
                    Instant.parse("2026-07-01T00:00:00Z"),
                    25,
                    "demo-owner",
                    "OWNER",
                    summary,
                    jobs,
                    operations,
                    List.of(),
                    List.of("/api/operator/model-usage-ledger", "/api/operator/model-usage-ledger/markdown/download"),
                    List.of("Raw media object keys, prompts, provider responses, and secrets are intentionally excluded.")
            );
    }

    private ModelUsageLedgerVo withJobsAndOperations(ModelUsageLedgerVo ledger) {
        return ledger(
                    ledger.summary(),
                    List.of(new ModelUsageLedgerJobVo(
                            "cost-job",
                            "cost-video",
                            "COMPLETED",
                            "zh-CN",
                            "full-demo",
                            4,
                            0,
                            0,
                            1,
                            400L,
                            new BigDecimal("0.12000000"),
                            Instant.parse("2026-07-01T00:01:00Z"),
                            List.of("/api/jobs/cost-job/ai-audit-package/download")
                    )),
                    List.of(new ModelUsageLedgerOperationVo(
                            "TRANSLATION",
                            "OPENAI",
                            "gpt-test",
                            "prompt-v1",
                            4,
                            0,
                            400L,
                            new BigDecimal("0.12000000"),
                            100L
                    ))
            );
    }

    private static final class StubModelUsageLedgerService implements ModelUsageLedgerService {
        private ModelUsageLedgerVo ledger;

        @Override
        public ModelUsageLedgerVo ledger(Integer limit) {
            return ledger;
        }

        @Override
        public String ledgerMarkdown(Integer limit) {
            return "# Ledger";
        }
    }

    private static final class StubRuntimeDependencySummaryService implements RuntimeDependencySummaryService {
        private BudgetReadinessVo budget;

        @Override
        public RuntimeDependencySummaryVo getSummary() {
            return new RuntimeDependencySummaryVo(
                    null,
                    null,
                    null,
                    null,
                    null,
                    new DemoReadinessVo(
                            false,
                            null,
                            null,
                            null,
                            budget,
                            new OwnerQuotaReadinessVo(true, 1, 1, true, BigDecimal.ONE),
                            Map.of(),
                            Map.of()
                    )
            );
        }
    }

    private static final class StubOwnerQuotaPreflightService implements OwnerQuotaPreflightService {
        private OwnerQuotaPreflightVo preflight;

        @Override
        public OwnerQuotaPreflightVo getPreflight() {
            return preflight;
        }

        @Override
        public void requireUploadAllowed() {
        }
    }
}
