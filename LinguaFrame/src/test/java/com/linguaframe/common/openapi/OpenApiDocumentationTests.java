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
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpenApiDocumentationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiDocsExposeBackendMetadataAndPrimaryDemoContract() {
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

        assertThat(body.get("components")).isInstanceOf(Map.class);
        Map<?, ?> components = (Map<?, ?>) body.get("components");
        assertThat(components.get("securitySchemes")).isInstanceOf(Map.class);
        Map<?, ?> securitySchemes = (Map<?, ?>) components.get("securitySchemes");
        assertThat(securitySchemes.get("DemoAccessToken")).isInstanceOf(Map.class);
        Map<?, ?> demoAccessToken = (Map<?, ?>) securitySchemes.get("DemoAccessToken");
        assertThat(demoAccessToken.get("type")).isEqualTo("apiKey");
        assertThat(demoAccessToken.get("in")).isEqualTo("header");
        assertThat(demoAccessToken.get("name")).isEqualTo("X-LinguaFrame-Demo-Token");
        assertThat((String) demoAccessToken.get("description")).contains("linguaframe.demo.access-token");

        assertThat(body.get("tags")).isInstanceOf(Iterable.class);
        Set<String> tagNames = StreamSupport.stream(((Iterable<?>) body.get("tags")).spliterator(), false)
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(tag -> String.valueOf(tag.get("name")))
                .collect(Collectors.toSet());
        assertThat(tagNames)
                .contains(
                        "Media Uploads",
                        "Localization Jobs",
                        "Runtime Dependencies",
                        "Demo Session",
                        "Prompt Templates",
                        "Operator Dashboard",
                        "Retention Cleanup"
                );

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
                        "/api/jobs",
                        "/api/jobs/{jobId}",
                        "/api/jobs/{jobId}/events",
                        "/api/jobs/{jobId}/retry",
                        "/api/jobs/{jobId}/cancel",
                        "/api/jobs/{jobId}/artifacts",
                        "/api/jobs/{jobId}/diagnostics/download",
                        "/api/jobs/{jobId}/evidence/markdown/download",
                        "/api/jobs/{jobId}/evidence/bundle/download",
                        "/api/jobs/{jobId}/quality-evaluation/evidence/markdown/download",
                        "/api/jobs/{jobId}/demo-run-package/download",
                        "/api/jobs/{jobId}/ai-audit-package/download",
                        "/api/jobs/{jobId}/delivery-manifest",
                        "/api/jobs/{jobId}/delivery-manifest/markdown/download",
                        "/api/jobs/{jobId}/handoff-package/download",
                        "/api/jobs/{jobId}/transcript",
                        "/api/jobs/{jobId}/subtitles/{language}",
                        "/api/jobs/{jobId}/subtitle-draft/publish",
                        "/api/jobs/{jobId}/artifacts/{artifactId}/download",
                        "/api/jobs/{jobId}/artifacts/archive/download",
                        "/api/demo-session",
                        "/api/demo-session/login",
                        "/api/demo-session/logout",
                        "/api/runtime/dependencies",
                        "/api/runtime/live-checks",
                        "/api/prompt-templates",
                        "/api/operator/dashboard",
                        "/api/operator/private-demo/operations",
                        "/api/retention/cleanup/preview",
                        "/api/retention/cleanup/run"
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
