package com.linguaframe.common.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RuntimeDependencyControllerTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

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
        assertThat(budget.get("estimatedCostTrackingEnabled")).isEqualTo(true);

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
                .doesNotContain("linguaframe_minio_password");
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
