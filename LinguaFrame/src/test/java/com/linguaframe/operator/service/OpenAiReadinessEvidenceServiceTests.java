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
import com.linguaframe.media.domain.vo.DemoUploadReadinessCheckVo;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.DemoUploadReadinessService;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerSummaryVo;
import com.linguaframe.operator.domain.vo.ModelUsageLedgerVo;
import com.linguaframe.operator.domain.vo.OpenAiReadinessEvidenceVo;
import com.linguaframe.operator.service.impl.OpenAiReadinessEvidenceServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiReadinessEvidenceServiceTests {

    private final StubRuntimeDependencySummaryService summaryService = new StubRuntimeDependencySummaryService();
    private final StubRuntimeLiveCheckService liveCheckService = new StubRuntimeLiveCheckService();
    private final StubDemoUploadReadinessService uploadReadinessService = new StubDemoUploadReadinessService();
    private final StubModelUsageLedgerService modelUsageLedgerService = new StubModelUsageLedgerService();
    private final OpenAiReadinessEvidenceService service = new OpenAiReadinessEvidenceServiceImpl(
            summaryService,
            liveCheckService,
            uploadReadinessService,
            modelUsageLedgerService
    );

    @Test
    void returnsReadyWhenOpenAiProvidersAndLiveCheckAreHealthy() {
        OpenAiReadinessEvidenceVo evidence = service.getEvidence();

        assertThat(evidence.overallStatus()).isEqualTo("READY");
        assertThat(evidence.phase()).isEqualTo("READY_FOR_OPENAI_SMOKE");
        assertThat(evidence.recommendedNextAction()).contains("docker-e2e-openai-smoke");
        assertThat(evidence.liveCheck().status()).isEqualTo("UP");
        assertThat(evidence.providers())
                .filteredOn(provider -> provider.paidProvider() && provider.enabled())
                .extracting("status")
                .containsOnly("READY");
        assertThat(evidence.commands())
                .extracting("command")
                .contains(
                        "scripts/demo/openai-demo-preflight.sh",
                        "scripts/demo/upload-readiness.sh",
                        "scripts/demo/model-usage-ledger.sh"
                );
    }

    @Test
    void returnsAttentionWhenOpenAiLiveCheckIsSkipped() {
        liveCheckService.checks.put("openai", new RuntimeProbeResultVo(
                RuntimeProbeStatus.SKIPPED,
                1L,
                "OpenAI connectivity check is disabled"
        ));

        OpenAiReadinessEvidenceVo evidence = service.getEvidence();

        assertThat(evidence.overallStatus()).isEqualTo("ATTENTION");
        assertThat(evidence.phase()).isEqualTo("NEEDS_CONNECTIVITY_PROOF");
        assertThat(evidence.recommendedNextAction()).contains("Enable the OpenAI connectivity check");
        assertThat(evidence.readinessSignals())
                .filteredOn(signal -> signal.id().equals("OPENAI_LIVE_CHECK"))
                .extracting("status")
                .containsExactly("ATTENTION");
    }

    @Test
    void returnsBlockedWhenProviderConfigOrUploadReadinessBlocks() {
        summaryService.summary = runtimeSummary(List.of(
                "/api/runtime/dependencies",
                "/api/runtime/live-checks"
        ), true, false);
        uploadReadinessService.readiness = new DemoUploadReadinessVo(
                "BLOCKED",
                "demo-owner",
                "tears-showcase",
                Instant.parse("2026-06-29T08:00:00Z"),
                List.of(new DemoUploadReadinessCheckVo(
                        "OWNER_QUOTA",
                        "Owner quota",
                        "BLOCKED",
                        "Daily provider budget is exhausted.",
                        "Wait for budget reset.",
                        true
                )),
                List.of("Wait for budget reset."),
                List.of("/api/media/uploads/readiness")
        );

        OpenAiReadinessEvidenceVo evidence = service.getEvidence();

        assertThat(evidence.overallStatus()).isEqualTo("BLOCKED");
        assertThat(evidence.phase()).isEqualTo("BLOCKED_BEFORE_PROVIDER_UPLOAD");
        assertThat(evidence.readinessSignals())
                .extracting("id")
                .contains("RUNTIME_CONTRACT", "OPENAI_PROVIDER_MODE", "UPLOAD_READINESS");
        assertThat(evidence.readinessSignals().toString())
                .contains("Missing required OpenAI readiness routes")
                .contains("translation")
                .contains("Daily provider budget is exhausted");
    }

    @Test
    void returnsSkippedWhenNoOpenAiProviderIsInRunPath() {
        summaryService.summary = runtimeSummary(requiredRoutes(), false, true);
        liveCheckService.checks.put("openai", new RuntimeProbeResultVo(
                RuntimeProbeStatus.SKIPPED,
                1L,
                "OpenAI connectivity check is disabled"
        ));

        OpenAiReadinessEvidenceVo evidence = service.getEvidence();

        assertThat(evidence.overallStatus()).isEqualTo("SKIPPED");
        assertThat(evidence.phase()).isEqualTo("DETERMINISTIC_DEMO_MODE");
        assertThat(evidence.recommendedNextAction()).contains("deterministic demo scripts");
        assertThat(evidence.readinessSignals())
                .filteredOn(signal -> signal.id().equals("OPENAI_PROVIDER_MODE"))
                .extracting("status")
                .containsExactly("SKIPPED");
    }

    @Test
    void evidenceMarkdownAndJsonDoNotExposeSecretsOrRawContent() throws Exception {
        liveCheckService.checks.put("openai", new RuntimeProbeResultVo(
                RuntimeProbeStatus.DOWN,
                1L,
                "Bearer sk-secret-token provider payload /Users/example/raw transcript text"
        ));

        OpenAiReadinessEvidenceVo evidence = service.getEvidence();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(evidence);
        String markdown = service.evidenceMarkdown();

        assertThat(json + markdown)
                .doesNotContain("sk-secret-token")
                .doesNotContain("Bearer sk-")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("provider request payload")
                .doesNotContain("provider response payload")
                .doesNotContain("source-videos/")
                .doesNotContain("job-artifacts/");
    }

    private RuntimeDependencySummaryVo runtimeSummary(List<String> requiredRoutes, boolean openAiProviders, boolean credentialsConfigured) {
        Map<String, ProviderReadinessVo> providers = new LinkedHashMap<>();
        String provider = openAiProviders ? "openai" : "demo";
        providers.put("transcription", new ProviderReadinessVo(true, provider, openAiProviders ? "whisper-1" : "demo-transcriber", credentialsConfigured));
        providers.put("translation", new ProviderReadinessVo(true, provider, openAiProviders ? "gpt-4.1-mini" : "demo-translator", credentialsConfigured));
        providers.put("evaluation", new ProviderReadinessVo(true, provider, openAiProviders ? "gpt-4.1-mini" : "demo-evaluator", credentialsConfigured));
        providers.put("tts", new ProviderReadinessVo(false, "demo", "demo-tts", false));

        Map<String, RuntimeFeatureFlagVo> features = new LinkedHashMap<>();
        features.put("costTracking", new RuntimeFeatureFlagVo(true));
        features.put("budgetGuard", new RuntimeFeatureFlagVo(true));

        return new RuntimeDependencySummaryVo(
                new RuntimeContractVo("0.0.1-SNAPSHOT", 26, requiredRoutes),
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
                        new OwnerQuotaReadinessVo(true, 1, 1, true, new BigDecimal("2.00000000")),
                        providers,
                        features
                )
        );
    }

    private List<String> requiredRoutes() {
        return List.of(
                "/api/runtime/dependencies",
                "/api/runtime/live-checks",
                "/api/media/uploads/readiness",
                "/api/operator/model-usage-ledger"
        );
    }

    private WorkerReadinessVo workerReadiness() {
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

    private ModelUsageLedgerVo ledger(String status, int calls, int failed) {
        return new ModelUsageLedgerVo(
                Instant.parse("2026-06-29T08:00:00Z"),
                20,
                "demo-owner",
                "demo-token-owner",
                new ModelUsageLedgerSummaryVo(
                        status,
                        calls > 0 ? 1 : 0,
                        calls,
                        failed,
                        0,
                        1,
                        1200L,
                        new BigDecimal("0.01000000"),
                        calls == 0 ? 0L : 600L,
                        calls == 0 ? BigDecimal.ZERO.setScale(2) : new BigDecimal("0.00"),
                        "Use the ledger links as cost and latency evidence."
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of("/api/operator/model-usage-ledger"),
                List.of("Safe model-call summaries only.")
        );
    }

    private final class StubRuntimeDependencySummaryService implements RuntimeDependencySummaryService {
        private RuntimeDependencySummaryVo summary = runtimeSummary(requiredRoutes(), true, true);

        @Override
        public RuntimeDependencySummaryVo getSummary() {
            return summary;
        }
    }

    private final class StubRuntimeLiveCheckService implements RuntimeLiveCheckService {
        private final Map<String, RuntimeProbeResultVo> checks = new LinkedHashMap<>(Map.of(
                "openai", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "OpenAI model metadata endpoint is reachable")
        ));

        @Override
        public RuntimeLiveCheckSummaryVo check() {
            return new RuntimeLiveCheckSummaryVo(
                    checks.values().stream().noneMatch(check -> check.status() == RuntimeProbeStatus.DOWN),
                    Instant.parse("2026-06-29T08:00:00Z"),
                    checks
            );
        }
    }

    private final class StubDemoUploadReadinessService implements DemoUploadReadinessService {
        private DemoUploadReadinessVo readiness = new DemoUploadReadinessVo(
                "READY",
                "demo-owner",
                "tears-showcase",
                Instant.parse("2026-06-29T08:00:00Z"),
                List.of(),
                List.of(),
                List.of("/api/media/uploads/readiness")
        );

        @Override
        public DemoUploadReadinessVo getReadiness(String demoProfileId) {
            return readiness;
        }
    }

    private final class StubModelUsageLedgerService implements ModelUsageLedgerService {
        private ModelUsageLedgerVo ledger = OpenAiReadinessEvidenceServiceTests.this.ledger("READY", 2, 0);

        @Override
        public ModelUsageLedgerVo ledger(Integer limit) {
            return ledger;
        }

        @Override
        public String ledgerMarkdown(Integer limit) {
            return "# Model usage ledger";
        }
    }
}
