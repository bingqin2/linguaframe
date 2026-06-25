package com.linguaframe.common.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
                .doesNotContain("linguaframe_dev_password")
                .doesNotContain("linguaframe_minio_password");
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
