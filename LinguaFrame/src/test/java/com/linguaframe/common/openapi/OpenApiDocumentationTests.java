package com.linguaframe.common.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocumentationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiDocsExposeBackendMetadata() {
        ResponseEntity<Map> response = restTemplate.getForEntity(url("/v3/api-docs"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();

        Map<?, ?> body = response.getBody();
        assertThat(body.get("openapi")).isInstanceOf(String.class);
        assertThat((String) body.get("openapi")).startsWith("3.");

        assertThat(body.get("info")).isInstanceOf(Map.class);
        Map<?, ?> info = (Map<?, ?>) body.get("info");
        assertThat(info.get("title")).isEqualTo("LinguaFrame API");
        assertThat(info.get("version")).isEqualTo("0.0.1");
        assertThat((String) info.get("description")).contains("video localization");

        assertThat(body.get("paths")).isInstanceOf(Map.class);
        Map<?, ?> paths = (Map<?, ?>) body.get("paths");
        Set<String> pathNames = paths.keySet().stream()
                .map(String::valueOf)
                .collect(Collectors.toSet());
        assertThat(pathNames)
                .contains(
                        "/api/media/uploads/validate",
                        "/api/media/uploads",
                        "/api/media/uploads/{videoId}",
                        "/api/jobs/{jobId}"
                );
    }

    @Test
    void swaggerUiIndexLoads() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/swagger-ui/index.html"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
