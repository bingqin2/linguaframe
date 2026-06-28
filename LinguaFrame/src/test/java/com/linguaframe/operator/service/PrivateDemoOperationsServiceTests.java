package com.linguaframe.operator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.BudgetReadinessVo;
import com.linguaframe.common.runtime.domain.vo.DemoReadinessVo;
import com.linguaframe.common.runtime.domain.vo.FfmpegReadinessVo;
import com.linguaframe.common.runtime.domain.vo.MediaReadinessVo;
import com.linguaframe.common.runtime.domain.vo.NetworkDependencyVo;
import com.linguaframe.common.runtime.domain.vo.OwnerQuotaReadinessVo;
import com.linguaframe.common.runtime.domain.vo.ProviderReadinessVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeContractVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeFeatureFlagVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.domain.vo.StorageDependencyVo;
import com.linguaframe.common.runtime.domain.vo.WorkerReadinessVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.job.domain.vo.RetentionCleanupResultVo;
import com.linguaframe.job.service.RetentionCleanupService;
import com.linguaframe.operator.domain.vo.OperatorCacheSummaryVo;
import com.linguaframe.operator.domain.vo.OperatorDashboardVo;
import com.linguaframe.operator.domain.vo.OperatorJobStatusCountVo;
import com.linguaframe.operator.domain.vo.OperatorModelCallSummaryVo;
import com.linguaframe.operator.domain.vo.PrivateDemoOperationsVo;
import com.linguaframe.operator.service.impl.PrivateDemoOperationsServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDemoOperationsServiceTests {

    private final StubRuntimeDependencySummaryService summaryService = new StubRuntimeDependencySummaryService();
    private final StubRuntimeLiveCheckService liveCheckService = new StubRuntimeLiveCheckService();
    private final StubOperatorDashboardService dashboardService = new StubOperatorDashboardService();
    private final StubRetentionCleanupService retentionCleanupService = new StubRetentionCleanupService();

    private final PrivateDemoOperationsService service = new PrivateDemoOperationsServiceImpl(
            summaryService,
            liveCheckService,
            dashboardService,
            retentionCleanupService
    );

    @Test
    void returnsReadyWhenPrivateDemoChecksAreHealthy() {
        PrivateDemoOperationsVo operations = service.operations();

        assertThat(operations.overallStatus()).isEqualTo("READY");
        assertThat(operations.readyCount()).isGreaterThan(0);
        assertThat(operations.blockedCount()).isZero();
        assertThat(operations.sections())
                .extracting("title")
                .contains(
                        "Access gate",
                        "Runtime contract",
                        "Live dependencies",
                        "Provider readiness",
                        "Cost safety",
                        "Storage and recovery",
                        "Retention cleanup",
                        "Demo evidence"
                );
        assertThat(operations.commands())
                .extracting("command")
                .contains(
                        "scripts/demo/private-demo-preflight.sh",
                        "scripts/demo/openai-demo-preflight.sh",
                        "scripts/demo/docker-e2e-success.sh",
                        "scripts/demo/private-demo-backup.sh --dry-run",
                        "scripts/demo/private-demo-restore.sh --dry-run --backup-dir <backup-dir>"
                );
    }

    @Test
    void reportsAttentionForSkippedOpenAiDisabledRetentionAndNoJobs() {
        liveCheckService.checks.put("openai", new RuntimeProbeResultVo(
                RuntimeProbeStatus.SKIPPED,
                1L,
                "OpenAI connectivity check is disabled"
        ));
        summaryService.summary = runtimeSummary(false);
        dashboardService.dashboard = emptyDashboard();

        PrivateDemoOperationsVo operations = service.operations();

        assertThat(operations.overallStatus()).isEqualTo("ATTENTION");
        assertThat(operations.attentionCount()).isGreaterThan(0);
        assertThat(operations.blockedCount()).isZero();
        assertThat(operations.sections().toString())
                .contains("OpenAI connectivity check is disabled")
                .contains("No demo jobs have been recorded yet")
                .contains("Retention cleanup is disabled");
    }

    @Test
    void reportsBlockedWhenRequiredDependencyIsDownOrRuntimeContractIsStale() {
        liveCheckService.checks.put("database", new RuntimeProbeResultVo(
                RuntimeProbeStatus.DOWN,
                1L,
                "Database query failed"
        ));
        summaryService.summary = runtimeSummary(true, List.of("/api/runtime/dependencies"));

        PrivateDemoOperationsVo operations = service.operations();

        assertThat(operations.overallStatus()).isEqualTo("BLOCKED");
        assertThat(operations.blockedCount()).isGreaterThanOrEqualTo(2);
        assertThat(operations.sections().toString())
                .contains("Database query failed")
                .contains("/api/operator/private-demo/operations is missing from the runtime contract");
    }

    @Test
    void doesNotExposeSecretsOrRawPaths() throws Exception {
        PrivateDemoOperationsVo operations = service.operations();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(operations);

        assertThat(json)
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("accessKey")
                .doesNotContain("secretKey")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider payload");
    }

    private RuntimeDependencySummaryVo runtimeSummary(boolean retentionEnabled) {
        return runtimeSummary(retentionEnabled, List.of(
                "/api/runtime/dependencies",
                "/api/runtime/live-checks",
                "/api/operator/dashboard",
                "/api/operator/private-demo/operations"
        ));
    }

    private RuntimeDependencySummaryVo runtimeSummary(boolean retentionEnabled, List<String> requiredRoutes) {
        Map<String, ProviderReadinessVo> providers = new LinkedHashMap<>();
        providers.put("transcription", new ProviderReadinessVo(true, "openai", "whisper-1", true));
        providers.put("translation", new ProviderReadinessVo(true, "openai", "gpt-4.1-mini", true));
        providers.put("tts", new ProviderReadinessVo(true, "openai", "gpt-4o-mini-tts", true));
        providers.put("evaluation", new ProviderReadinessVo(true, "openai", "gpt-4.1-mini", true));

        Map<String, RuntimeFeatureFlagVo> features = new LinkedHashMap<>();
        features.put("jobStatusCache", new RuntimeFeatureFlagVo(true));
        features.put("uploadRateLimit", new RuntimeFeatureFlagVo(true));
        features.put("retentionCleanup", new RuntimeFeatureFlagVo(retentionEnabled));
        features.put("costTracking", new RuntimeFeatureFlagVo(true));
        features.put("budgetGuard", new RuntimeFeatureFlagVo(true));
        features.put("dailyBudgetGuard", new RuntimeFeatureFlagVo(true));

        return new RuntimeDependencySummaryVo(
                new RuntimeContractVo("0.0.1-SNAPSHOT", 19, requiredRoutes),
                new NetworkDependencyVo("mysql", "localhost", 3306),
                new NetworkDependencyVo("redis", "localhost", 6379),
                new NetworkDependencyVo("rabbitmq", "localhost", 5672),
                new StorageDependencyVo("minio", "http://localhost:9000", "linguaframe-artifacts"),
                new DemoReadinessVo(
                        true,
                        workerReadiness(),
                        new MediaReadinessVo(100, 300),
                        new FfmpegReadinessVo(true, true, true, true, 120, 180),
                        new BudgetReadinessVo(true, new BigDecimal("0.50000000"), true,
                                new BigDecimal("2.00000000"), "demo-owner", true),
                        new OwnerQuotaReadinessVo(false, 0, 0, false, BigDecimal.ZERO),
                        providers,
                        features
                )
        );
    }

    private static WorkerReadinessVo workerReadiness() {
        return new WorkerReadinessVo(
                true,
                true,
                WorkerRole.COMBINED,
                2,
                10,
                5000L,
                "linguaframe.localization.jobs",
                "linguaframe.jobs",
                "linguaframe.localization.jobs",
                "localization.queued",
                "linguaframe.localization.jobs",
                "localization.queued",
                "linguaframe.localization.openai.jobs",
                "localization.openai",
                List.of("COMBINED:ALL"),
                List.of("LINGUAFRAME_WORKER_ROLE=COMBINED docker compose --env-file .env up -d linguaframe-backend")
        );
    }

    private OperatorDashboardVo dashboard() {
        return new OperatorDashboardVo(
                List.of(new OperatorJobStatusCountVo(com.linguaframe.job.domain.enums.LocalizationJobStatus.COMPLETED, 1)),
                List.of(),
                new OperatorModelCallSummaryVo(4, 0, 1200, new BigDecimal("0.01000000")),
                new OperatorCacheSummaryVo(1, 3, 2),
                List.of()
        );
    }

    private OperatorDashboardVo emptyDashboard() {
        return new OperatorDashboardVo(
                List.of(),
                List.of(),
                new OperatorModelCallSummaryVo(0, 0, 0, BigDecimal.ZERO),
                new OperatorCacheSummaryVo(0, 0, 0),
                List.of()
        );
    }

    private final class StubRuntimeDependencySummaryService implements RuntimeDependencySummaryService {
        private RuntimeDependencySummaryVo summary = runtimeSummary(true);

        @Override
        public RuntimeDependencySummaryVo getSummary() {
            return summary;
        }
    }

    private final class StubRuntimeLiveCheckService implements RuntimeLiveCheckService {
        private final Map<String, RuntimeProbeResultVo> checks = new LinkedHashMap<>(Map.of(
                "database", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "Database query succeeded"),
                "redis", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "Redis ping succeeded"),
                "rabbitmq", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "RabbitMQ connection opened"),
                "minio", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "MinIO bucket is reachable"),
                "ffmpeg", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "FFmpeg binary responded"),
                "openai", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "OpenAI model metadata endpoint is reachable")
        ));

        @Override
        public RuntimeLiveCheckSummaryVo check() {
            return new RuntimeLiveCheckSummaryVo(
                    checks.values().stream().noneMatch(check -> check.status() == RuntimeProbeStatus.DOWN),
                    Instant.parse("2026-06-28T00:00:00Z"),
                    checks
            );
        }
    }

    private final class StubOperatorDashboardService implements OperatorDashboardService {
        private OperatorDashboardVo dashboard = PrivateDemoOperationsServiceTests.this.dashboard();

        @Override
        public OperatorDashboardVo dashboard() {
            return dashboard;
        }
    }

    private final class StubRetentionCleanupService implements RetentionCleanupService {
        @Override
        public RetentionCleanupResultVo previewCleanup() {
            return new RetentionCleanupResultVo(true, 2, 0, 0, 0, 0, 0);
        }

        @Override
        public RetentionCleanupResultVo runCleanup() {
            throw new UnsupportedOperationException("Operations readiness must not run cleanup.");
        }
    }
}
