package com.linguaframe.media.service;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.quota.OwnerQuotaLimitVo;
import com.linguaframe.common.quota.OwnerQuotaPreflightService;
import com.linguaframe.common.quota.OwnerQuotaPreflightVo;
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
import com.linguaframe.demo.domain.vo.DemoRunProfileVo;
import com.linguaframe.demo.service.DemoRunProfileService;
import com.linguaframe.job.domain.enums.WorkerRole;
import com.linguaframe.media.domain.vo.DemoUploadReadinessVo;
import com.linguaframe.media.service.impl.DemoUploadReadinessServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DemoUploadReadinessServiceTests {

    private final LinguaFrameProperties properties = new LinguaFrameProperties();
    private StubRuntimeDependencySummaryService runtimeDependencySummaryService = new StubRuntimeDependencySummaryService();
    private StubRuntimeLiveCheckService runtimeLiveCheckService = new StubRuntimeLiveCheckService();
    private StubOwnerQuotaPreflightService ownerQuotaPreflightService = new StubOwnerQuotaPreflightService();
    private StubDemoRunProfileService demoRunProfileService = new StubDemoRunProfileService();

    @Test
    void reportsReadyWhenOwnerSessionDependenciesQuotaAndProfileAreReady() {
        DemoUploadReadinessVo readiness = service().getReadiness("quick-baseline");

        assertThat(readiness.overallStatus()).isEqualTo("READY");
        assertThat(readiness.ownerId()).isEqualTo("demo-owner");
        assertThat(readiness.demoProfileId()).isEqualTo("quick-baseline");
        assertThat(readiness.checks())
                .extracting("id")
                .contains("owner-session", "runtime-contract", "live-dependencies", "owner-quota", "demo-profile");
        assertThat(readiness.checks())
                .filteredOn(check -> check.blocking())
                .isEmpty();
        assertThat(readiness.requiredActions()).contains("Upload can start after file validation passes.");
        assertThat(readiness.evidenceRoutes())
                .contains("/api/media/uploads/readiness", "/api/media/uploads/preflight");
    }

    @Test
    void reportsReadyWhenAccessGateRequestReachesReadinessEndpoint() {
        properties.getDemo().setAccessToken("private-demo-token");

        DemoUploadReadinessVo readiness = service().getReadiness("quick-baseline");

        assertThat(readiness.overallStatus()).isEqualTo("READY");
        assertThat(readiness.checks())
                .filteredOn(check -> check.id().equals("owner-session"))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("READY");
                    assertThat(check.blocking()).isFalse();
                    assertThat(check.detail()).contains("only returned after API access passes");
                });
    }

    @Test
    void blocksWhenOwnerQuotaPreflightIsBlocked() {
        ownerQuotaPreflightService.preflight = new OwnerQuotaPreflightVo(
                "demo-owner",
                true,
                false,
                2,
                1,
                new BigDecimal("0.25"),
                LocalDate.parse("2026-06-28"),
                List.of(new OwnerQuotaLimitVo("activeJobs", true, BigDecimal.valueOf(2), BigDecimal.valueOf(2))),
                List.of("Active job limit reached for owner demo-owner: current 2, limit 2.")
        );

        DemoUploadReadinessVo readiness = service().getReadiness("quick-baseline");

        assertThat(readiness.overallStatus()).isEqualTo("BLOCKED");
        assertThat(readiness.checks())
                .filteredOn(check -> check.id().equals("owner-quota"))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.detail()).contains("Active job limit reached");
                    assertThat(check.blocking()).isTrue();
                });
    }

    @Test
    void blocksUnknownDemoProfile() {
        DemoUploadReadinessVo readiness = service().getReadiness("missing-profile");

        assertThat(readiness.overallStatus()).isEqualTo("BLOCKED");
        assertThat(readiness.checks())
                .filteredOn(check -> check.id().equals("demo-profile"))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("BLOCKED");
                    assertThat(check.detail()).contains("missing-profile");
                    assertThat(check.blocking()).isTrue();
                });
    }

    @Test
    void warnsWhenPaidProvidersAreEnabledButOpenAiLiveCheckIsSkipped() {
        runtimeDependencySummaryService.translationProvider = new ProviderReadinessVo(
                true,
                "openai",
                "gpt-4.1-mini",
                true
        );
        runtimeLiveCheckService.checks.put(
                "openai",
                new RuntimeProbeResultVo(RuntimeProbeStatus.SKIPPED, 1L, "OpenAI connectivity check is disabled")
        );

        DemoUploadReadinessVo readiness = service().getReadiness("quick-baseline");

        assertThat(readiness.overallStatus()).isEqualTo("ATTENTION");
        assertThat(readiness.checks())
                .filteredOn(check -> check.id().equals("paid-provider-check"))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.status()).isEqualTo("ATTENTION");
                    assertThat(check.blocking()).isFalse();
                    assertThat(check.nextAction()).contains("OpenAI preflight");
                });
    }

    private DemoUploadReadinessService service() {
        return new DemoUploadReadinessServiceImpl(
                properties,
                runtimeDependencySummaryService,
                runtimeLiveCheckService,
                ownerQuotaPreflightService,
                demoRunProfileService
        );
    }

    private static final class StubRuntimeDependencySummaryService implements RuntimeDependencySummaryService {
        private ProviderReadinessVo translationProvider = new ProviderReadinessVo(true, "demo", "", false);

        @Override
        public RuntimeDependencySummaryVo getSummary() {
            Map<String, ProviderReadinessVo> providers = new LinkedHashMap<>();
            providers.put("transcription", new ProviderReadinessVo(true, "demo", "", false));
            providers.put("translation", translationProvider);
            providers.put("tts", new ProviderReadinessVo(false, "demo", "", false));
            providers.put("evaluation", new ProviderReadinessVo(false, "demo", "", false));
            return new RuntimeDependencySummaryVo(
                    new RuntimeContractVo(
                            "0.0.1-SNAPSHOT",
                            25,
                            List.of("/api/media/uploads", "/api/media/uploads/preflight")
                    ),
                    new NetworkDependencyVo("mysql", "localhost", 3306),
                    new NetworkDependencyVo("redis", "localhost", 6379),
                    new NetworkDependencyVo("rabbitmq", "localhost", 5672),
                    new StorageDependencyVo("minio", "http://localhost:9000", "linguaframe-artifacts"),
                    new DemoReadinessVo(
                            false,
                            new WorkerReadinessVo(true, true, WorkerRole.COMBINED, 2, 10, 5000),
                            new MediaReadinessVo(100, 300),
                            new FfmpegReadinessVo(true, true, true, true, 120, 180),
                            new BudgetReadinessVo(false, BigDecimal.ZERO, false, BigDecimal.ZERO, "demo-owner", true),
                            new OwnerQuotaReadinessVo(false, 0, 0, false, BigDecimal.ZERO),
                            providers,
                            Map.of(
                                    "jobStatusCache", new RuntimeFeatureFlagVo(true),
                                    "ownerQuota", new RuntimeFeatureFlagVo(false)
                            )
                    )
            );
        }
    }

    private static final class StubRuntimeLiveCheckService implements RuntimeLiveCheckService {
        private final Map<String, RuntimeProbeResultVo> checks = new LinkedHashMap<>();

        private StubRuntimeLiveCheckService() {
            checks.put("database", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "Database query succeeded"));
            checks.put("redis", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "Redis ping succeeded"));
            checks.put("rabbitmq", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "RabbitMQ connection opened"));
            checks.put("minio", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "MinIO bucket is reachable"));
            checks.put("ffmpeg", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "FFmpeg binary responded"));
            checks.put("openai", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "OpenAI model metadata reachable"));
        }

        @Override
        public RuntimeLiveCheckSummaryVo check() {
            return new RuntimeLiveCheckSummaryVo(
                    checks.values().stream().noneMatch(check -> check.status() == RuntimeProbeStatus.DOWN),
                    Instant.parse("2026-06-28T00:00:00Z"),
                    checks
            );
        }
    }

    private static final class StubOwnerQuotaPreflightService implements OwnerQuotaPreflightService {
        private OwnerQuotaPreflightVo preflight = new OwnerQuotaPreflightVo(
                "demo-owner",
                false,
                true,
                0,
                0,
                BigDecimal.ZERO,
                LocalDate.parse("2026-06-28"),
                List.of(),
                List.of()
        );

        @Override
        public OwnerQuotaPreflightVo getPreflight() {
            return preflight;
        }

        @Override
        public void requireUploadAllowed() {
        }
    }

    private static final class StubDemoRunProfileService implements DemoRunProfileService {
        @Override
        public List<DemoRunProfileVo> listProfiles() {
            return List.of(profile("quick-baseline"));
        }

        @Override
        public Optional<DemoRunProfileVo> findById(String id) {
            return listProfiles().stream()
                    .filter(profile -> profile.id().equals(id))
                    .findFirst();
        }

        @Override
        public String normalizeProfileId(String id) {
            return id == null || id.isBlank() ? "quick-baseline" : id.trim();
        }

        private DemoRunProfileVo profile(String id) {
            return new DemoRunProfileVo(
                    id,
                    "Quick baseline",
                    "Fast deterministic baseline.",
                    "zh-CN",
                    "",
                    "NATURAL",
                    "STANDARD",
                    "OFF",
                    ""
            );
        }
    }
}
