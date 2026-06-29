package com.linguaframe.common.runtime;

import com.linguaframe.common.runtime.domain.enums.RuntimeProbeStatus;
import com.linguaframe.common.runtime.domain.vo.RuntimeLiveCheckSummaryVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeProbeResultVo;
import com.linguaframe.common.runtime.service.RuntimeLiveCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RuntimeDependencyControllerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private RuntimeLiveCheckService liveCheckService;

    @Test
    void runtimeDependenciesReturnsSanitizedConfiguration() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/runtime/dependencies"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Map<?, ?> body = response.getBody();
        assertNetworkDependency(body.get("database"), "mysql", "localhost", 3306);
        assertNetworkDependency(body.get("redis"), "redis", "localhost", 6379);
        assertNetworkDependency(body.get("rabbitmq"), "rabbitmq", "localhost", 5672);

        assertThat(body.get("storage")).isInstanceOf(Map.class);
        Map<?, ?> storage = (Map<?, ?>) body.get("storage");
        assertThat(storage.get("type")).isEqualTo("minio");
        assertThat(storage.get("endpoint")).isEqualTo("http://localhost:9000");
        assertThat(storage.get("bucket")).isEqualTo("linguaframe-artifacts");

        assertThat(body.get("runtime")).isInstanceOf(Map.class);
        Map<?, ?> runtime = (Map<?, ?>) body.get("runtime");
        assertThat(runtime.get("appVersion")).isEqualTo("0.0.1-SNAPSHOT");
        assertThat(runtime.get("latestMigrationVersion")).isEqualTo(28);
        assertThat((Iterable<String>) runtime.get("requiredRoutes"))
                .contains(
                        "/api/runtime/dependencies",
                        "/api/runtime/live-checks",
                        "/api/demo-run-profiles",
                        "/api/media/uploads/preflight",
                        "/api/media/uploads/readiness",
                        "/api/media/uploads",
                        "/api/media/uploads/{videoId}/source/download",
                        "/api/jobs/{jobId}",
                        "/api/jobs/{jobId}/diagnostics/download",
                        "/api/jobs/{jobId}/evidence/markdown/download",
                        "/api/jobs/{jobId}/evidence/bundle/download",
                        "/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download",
                        "/api/jobs/{jobId}/subtitle-review-evidence",
                        "/api/jobs/{jobId}/subtitle-review-evidence/markdown/download",
                        "/api/jobs/{jobId}/subtitle-review-evidence/download",
                        "/api/jobs/{jobId}/narration-workspace",
                        "/api/jobs/{jobId}/narration-workspace/generate-audio",
                        "/api/jobs/{jobId}/narration-evidence",
                        "/api/jobs/{jobId}/narration-evidence/markdown/download",
                        "/api/jobs/{jobId}/narration-evidence/download",
                        "/api/jobs/{jobId}/openai-smoke-proof",
                        "/api/jobs/{jobId}/openai-smoke-proof/markdown/download",
                        "/api/jobs/{jobId}/demo-reviewer-workspace",
                        "/api/jobs/{jobId}/demo-reviewer-workspace/markdown/download",
                        "/api/jobs/{jobId}/demo-reviewer-workspace/download",
                        "/api/jobs/{jobId}/demo-handoff-portal",
                        "/api/jobs/{jobId}/demo-handoff-portal/markdown/download",
                        "/api/jobs/{jobId}/demo-handoff-portal/download",
                        "/api/jobs/{jobId}/demo-run-package/download",
                        "/api/jobs/{jobId}/demo-run-snapshot",
                        "/api/jobs/{jobId}/demo-run-snapshot/download",
                        "/api/jobs/{jobId}/ai-audit-package/download",
                        "/api/jobs/{jobId}/demo-run-matrix",
                        "/api/jobs/{jobId}/demo-run-monitor",
                        "/api/jobs/{jobId}/demo-run-monitor/markdown/download",
                        "/api/jobs/{jobId}/demo-presenter-pack",
                        "/api/jobs/{jobId}/comparison/{comparisonJobId}",
                        "/api/jobs/{jobId}/comparison/{comparisonJobId}/markdown/download",
                        "/api/jobs/{jobId}/delivery-manifest",
                        "/api/jobs/{jobId}/delivery-manifest/markdown/download",
                        "/api/jobs/{jobId}/handoff-package/download",
                        "/api/jobs/{jobId}/artifacts/archive/download",
                        "/api/operator/private-demo/operations",
                        "/api/operator/private-demo/launch-rehearsal",
                        "/api/operator/private-demo/evidence-gallery",
                        "/api/operator/private-demo/run-archive",
                        "/api/operator/demo-sample-media-catalog",
                        "/api/operator/demo-run-launcher"
                );

        assertThat(body.get("readiness")).isInstanceOf(Map.class);
        Map<?, ?> readiness = (Map<?, ?>) body.get("readiness");
        assertThat(readiness.get("demoAccessGate")).isEqualTo(false);

        assertThat(readiness.get("worker")).isInstanceOf(Map.class);
        Map<?, ?> worker = (Map<?, ?>) readiness.get("worker");
        assertThat(worker.get("dispatchEnabled")).isEqualTo(false);
        assertThat(worker.get("executionEnabled")).isEqualTo(false);
        assertThat(worker.get("role")).isEqualTo("COMBINED");
        assertThat(worker.get("maxRetries")).isEqualTo(2);
        assertThat(worker.get("dispatchBatchSize")).isEqualTo(10);
        assertThat(worker.get("dispatchIntervalMs")).isEqualTo(5000);
        assertThat(worker.get("listenerQueue")).isEqualTo("linguaframe.localization.jobs");
        assertThat(worker.get("jobExchange")).isEqualTo("linguaframe.jobs");
        assertThat(worker.get("defaultJobQueue")).isEqualTo("linguaframe.localization.jobs");
        assertThat(worker.get("defaultRoutingKey")).isEqualTo("localization.queued");
        assertThat(worker.get("ffmpegJobQueue")).isEqualTo("linguaframe.localization.jobs");
        assertThat(worker.get("ffmpegRoutingKey")).isEqualTo("localization.queued");
        assertThat(worker.get("openaiJobQueue")).isEqualTo("linguaframe.localization.openai.jobs");
        assertThat(worker.get("openaiRoutingKey")).isEqualTo("localization.openai");
        assertThat((Iterable<String>) worker.get("ownedStageGroups"))
                .contains(
                        "COMBINED:ALL",
                        "FFMPEG:WORKER_SMOKE,AUDIO_EXTRACTION,SUBTITLE_BURN_IN,DUBBED_VIDEO_DELIVERY,ARTIFACT_SUMMARY",
                        "OPENAI:TRANSCRIPT_SUBTITLE_EXPORT,TARGET_SUBTITLE_EXPORT,SUBTITLE_POLISHING,QUALITY_EVALUATION,DUBBING_AUDIO_GENERATION"
                );
        assertThat((Iterable<String>) worker.get("recommendedCommands"))
                .contains(
                        "LINGUAFRAME_WORKER_ROLE=COMBINED docker compose --env-file .env up -d linguaframe-backend",
                        "LINGUAFRAME_WORKER_ROLE=FFMPEG LINGUAFRAME_RABBITMQ_LISTENER_QUEUE=linguaframe.localization.jobs docker compose --env-file .env up -d linguaframe-backend",
                        "LINGUAFRAME_WORKER_ROLE=OPENAI LINGUAFRAME_RABBITMQ_LISTENER_QUEUE=linguaframe.localization.openai.jobs docker compose --env-file .env up -d linguaframe-backend"
                );

        assertThat(readiness.get("media")).isInstanceOf(Map.class);
        Map<?, ?> media = (Map<?, ?>) readiness.get("media");
        assertThat(media.get("maxFileSizeMb")).isEqualTo(100);
        assertThat(media.get("maxDurationSeconds")).isEqualTo(300);

        assertThat(readiness.get("ffmpeg")).isInstanceOf(Map.class);
        Map<?, ?> ffmpeg = (Map<?, ?>) readiness.get("ffmpeg");
        assertThat(ffmpeg.get("audioEnabled")).isEqualTo(false);
        assertThat(ffmpeg.get("burnInEnabled")).isEqualTo(false);
        assertThat(ffmpeg.get("binaryConfigured")).isEqualTo(true);
        assertThat(ffmpeg.get("workspaceConfigured")).isEqualTo(true);
        assertThat(ffmpeg.get("audioTimeoutSeconds")).isEqualTo(120);
        assertThat(ffmpeg.get("burnInTimeoutSeconds")).isEqualTo(180);

        assertThat(readiness.get("budget")).isInstanceOf(Map.class);
        Map<?, ?> budget = (Map<?, ?>) readiness.get("budget");
        assertThat(budget.get("enabled")).isEqualTo(false);
        assertThat(budget.get("maxJobCostUsd")).isEqualTo(0);
        assertThat(budget.get("dailyBudgetGuardEnabled")).isEqualTo(false);
        assertThat(budget.get("maxDailyCostUsd")).isEqualTo(0);
        assertThat(budget.get("budgetIdentity")).isEqualTo("demo-owner");
        assertThat(budget.get("estimatedCostTrackingEnabled")).isEqualTo(true);

        assertThat(readiness.get("ownerQuota")).isInstanceOf(Map.class);
        Map<?, ?> ownerQuota = (Map<?, ?>) readiness.get("ownerQuota");
        assertThat(ownerQuota.get("enabled")).isEqualTo(false);
        assertThat(ownerQuota.get("maxActiveJobs")).isEqualTo(0);
        assertThat(ownerQuota.get("maxQueuedJobs")).isEqualTo(0);
        assertThat(ownerQuota.get("dailyBudgetGuardEnabled")).isEqualTo(false);
        assertThat(ownerQuota.get("maxDailyCostUsd")).isEqualTo(0);

        assertThat(readiness.get("providers")).isInstanceOf(Map.class);
        Map<?, ?> providers = (Map<?, ?>) readiness.get("providers");
        assertProviderReadiness(providers.get("transcription"), false, "demo", false);
        assertProviderReadiness(providers.get("translation"), false, "demo", false);
        assertProviderReadiness(providers.get("tts"), false, "demo", false);
        assertProviderReadiness(providers.get("evaluation"), false, "demo", false);

        assertThat(readiness.get("features")).isInstanceOf(Map.class);
        Map<?, ?> features = (Map<?, ?>) readiness.get("features");
        assertFeatureFlag(features.get("jobStatusCache"), true);
        assertFeatureFlag(features.get("uploadRateLimit"), false);
        assertFeatureFlag(features.get("retentionCleanup"), false);
        assertFeatureFlag(features.get("costTracking"), true);
        assertFeatureFlag(features.get("budgetGuard"), false);
        assertFeatureFlag(features.get("dailyBudgetGuard"), false);
        assertFeatureFlag(features.get("ownerQuota"), false);
    }

    @Test
    void runtimeDependenciesDoesNotExposeSecrets() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/runtime/dependencies"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody())
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("accessKey")
                .doesNotContain("secretKey")
                .doesNotContain("apiKey")
                .doesNotContain("accessToken")
                .doesNotContain("workDir")
                .doesNotContain("/tmp/linguaframe-media")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("linguaframe_dev_password")
                .doesNotContain("linguaframe_minio_password")
                .doesNotContain("X-LinguaFrame-Demo-Token");
    }

    @Test
    void runtimeLiveChecksReturnsSafeProbeSummary() {
        when(liveCheckService.check()).thenReturn(liveChecks());

        ResponseEntity<Map> response = restTemplate.getForEntity(url("/api/runtime/live-checks"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("healthy")).isEqualTo(true);
        assertThat(response.getBody().get("checkedAt")).isEqualTo("2026-06-28T00:00:00Z");

        assertThat(response.getBody().get("checks")).isInstanceOf(Map.class);
        Map<?, ?> checks = (Map<?, ?>) response.getBody().get("checks");
        assertProbe(checks.get("database"), "UP", "Database query succeeded");
        assertProbe(checks.get("redis"), "UP", "Redis ping succeeded");
        assertProbe(checks.get("rabbitmq"), "UP", "RabbitMQ connection opened");
        assertProbe(checks.get("minio"), "UP", "MinIO bucket is reachable");
        assertProbe(checks.get("ffmpeg"), "UP", "FFmpeg binary responded");
        assertProbe(checks.get("openai"), "SKIPPED", "OpenAI connectivity check is disabled");
    }

    private void assertProviderReadiness(Object value, boolean enabled, String provider, boolean credentialsConfigured) {
        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> dependency = (Map<?, ?>) value;
        assertThat(dependency.get("enabled")).isEqualTo(enabled);
        assertThat(dependency.get("provider")).isEqualTo(provider);
        assertThat(dependency.get("credentialsConfigured")).isEqualTo(credentialsConfigured);
    }

    private void assertFeatureFlag(Object value, boolean enabled) {
        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> dependency = (Map<?, ?>) value;
        assertThat(dependency.get("enabled")).isEqualTo(enabled);
    }

    private void assertNetworkDependency(Object value, String type, String host, int port) {
        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> dependency = (Map<?, ?>) value;
        assertThat(dependency.get("type")).isEqualTo(type);
        assertThat(dependency.get("host")).isEqualTo(host);
        assertThat(dependency.get("port")).isEqualTo(port);
    }

    private void assertProbe(Object value, String status, String message) {
        assertThat(value).isInstanceOf(Map.class);
        Map<?, ?> probe = (Map<?, ?>) value;
        assertThat(probe.get("status")).isEqualTo(status);
        assertThat(probe.get("message")).isEqualTo(message);
        assertThat(probe.get("latencyMs")).isEqualTo(1);
    }

    private RuntimeLiveCheckSummaryVo liveChecks() {
        Map<String, RuntimeProbeResultVo> checks = new LinkedHashMap<>();
        checks.put("database", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "Database query succeeded"));
        checks.put("redis", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "Redis ping succeeded"));
        checks.put("rabbitmq", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "RabbitMQ connection opened"));
        checks.put("minio", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "MinIO bucket is reachable"));
        checks.put("ffmpeg", new RuntimeProbeResultVo(RuntimeProbeStatus.UP, 1L, "FFmpeg binary responded"));
        checks.put("openai", new RuntimeProbeResultVo(RuntimeProbeStatus.SKIPPED, 1L, "OpenAI connectivity check is disabled"));
        return new RuntimeLiveCheckSummaryVo(true, Instant.parse("2026-06-28T00:00:00Z"), checks);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
