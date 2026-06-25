# OpenAPI Documentation Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose machine-readable OpenAPI docs and Swagger UI for the current LinguaFrame backend.

**Architecture:** Add Springdoc to the existing Spring MVC backend and configure a small OpenAPI metadata bean under `com.linguaframe.common.openapi`. Verify `/v3/api-docs` and `/swagger-ui/index.html` through HTTP tests so future upload APIs have a documented contract surface from the start.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Maven, Spring Web MVC, Springdoc OpenAPI 2.8.17, JUnit 5, Spring Boot Test, AssertJ.

## Global Constraints

- Run Maven commands from the repository root.
- Keep backend packages under `com.linguaframe`.
- Use `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`; do not use Springdoc 3.0.x because its parent targets Spring Boot 4.x while this project uses Spring Boot 3.5.15.
- Do not add database, Redis, RabbitMQ, MinIO, OpenAI, Docker, frontend, upload, worker, or FFmpeg behavior in this slice.
- Do not commit secrets or real environment-specific values.
- Do not put agent workflow preferences in `AGENTS.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## File Structure

- Modify: `LinguaFrame/pom.xml`
  - Responsibility: add the Springdoc dependency version and webmvc-ui starter.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java`
  - Responsibility: define stable API documentation title, version, and description.
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`
  - Responsibility: prove `/v3/api-docs` returns LinguaFrame metadata and Swagger UI loads.
- Modify: `README.md`
  - Responsibility: document local OpenAPI and Swagger UI URLs.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record this feature slice and validation commands.

## Task 1: Add Failing OpenAPI HTTP Tests

**Files:**
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`

**Interfaces:**
- Consumes: existing Spring Boot web test setup and `TestRestTemplate`.
- Produces: tests that require `/v3/api-docs` and `/swagger-ui/index.html` to exist.

- [ ] **Step 1: Create the failing test**

Create `LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java`:

```java
package com.linguaframe.common.openapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
```

- [ ] **Step 2: Verify the test fails before Springdoc is added**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=OpenApiDocumentationTests
```

Expected: test execution fails because `/v3/api-docs` and `/swagger-ui/index.html` return `404 NOT_FOUND` instead of `200 OK`.

## Task 2: Add Springdoc And API Metadata

**Files:**
- Modify: `LinguaFrame/pom.xml`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java`

**Interfaces:**
- Consumes: Spring Boot MVC auto-configuration and Springdoc starter.
- Produces: `/v3/api-docs` JSON with `info.title = LinguaFrame API` and a local Swagger UI page.

- [ ] **Step 1: Add the Springdoc version property**

Add the Springdoc version inside `<properties>` in `LinguaFrame/pom.xml`:

```xml
<properties>
    <java.version>21</java.version>
    <springdoc-openapi.version>2.8.17</springdoc-openapi.version>
</properties>
```

- [ ] **Step 2: Add the Springdoc Web MVC UI dependency**

Add this dependency after `spring-boot-starter-actuator` in `LinguaFrame/pom.xml`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc-openapi.version}</version>
</dependency>
```

- [ ] **Step 3: Add OpenAPI metadata configuration**

Create `LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java`:

```java
package com.linguaframe.common.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI linguaFrameOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LinguaFrame API")
                        .version("0.0.1")
                        .description("API documentation for the LinguaFrame video localization backend."));
    }
}
```

- [ ] **Step 4: Verify the OpenAPI tests pass**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=OpenApiDocumentationTests
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

## Task 3: Document The API Documentation Surface

**Files:**
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: the verified `/v3/api-docs` and `/swagger-ui/index.html` endpoints.
- Produces: contributor-facing commands and execution evidence.

- [ ] **Step 1: Add README API documentation notes**

Insert this section after `Runtime Configuration` in `README.md`:

```markdown
## API Documentation

When the backend is running, OpenAPI documentation is available at:

```bash
curl http://localhost:8080/v3/api-docs
```

Swagger UI is available in the browser:

```text
http://localhost:8080/swagger-ui/index.html
```
```

- [ ] **Step 2: Add execution log entry**

Append this entry to `docs/progress/execution-log.md` after successful verification:

```markdown
## 2026-06-25

Work:

- Added Springdoc OpenAPI Web MVC UI dependency using version `2.8.17`.
- Added `OpenApiConfiguration` with LinguaFrame API title, version, and description.
- Added HTTP coverage for `/v3/api-docs` and `/swagger-ui/index.html`.
- Documented local API documentation URLs in `README.md`.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=OpenApiDocumentationTests` failed before implementation because the documentation endpoints returned `404 NOT_FOUND`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=OpenApiDocumentationTests` passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 6, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add application API controllers.
- Future upload/job APIs should appear in the generated OpenAPI document automatically.
```

## Task 4: Final Verification And Commit

**Files:**
- Verify: all files touched by Tasks 1-3.

**Interfaces:**
- Consumes: completed implementation and documentation updates.
- Produces: one complete feature commit.

- [ ] **Step 1: Run the full test suite**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: root Maven build passes with `Tests run: 6, Failures: 0, Errors: 0`.

- [ ] **Step 2: Review changed files**

Run:

```bash
git status --short
git diff -- LinguaFrame/pom.xml LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java README.md docs/progress/execution-log.md docs/plans/005-openapi-documentation-foundation.md
git diff --check
```

Expected: only this feature slice's files are modified or created, and `git diff --check` reports no whitespace errors.

- [ ] **Step 3: Commit the feature**

Run:

```bash
git add LinguaFrame/pom.xml \
  LinguaFrame/src/main/java/com/linguaframe/common/openapi/OpenApiConfiguration.java \
  LinguaFrame/src/test/java/com/linguaframe/common/openapi/OpenApiDocumentationTests.java \
  README.md \
  docs/progress/execution-log.md \
  docs/plans/005-openapi-documentation-foundation.md
git commit -m "Add OpenAPI documentation foundation"
```

Expected: one commit containing the complete OpenAPI documentation foundation feature.

## Self-Review

- Spec coverage: This plan advances the basic version by adding the OpenAPI dependency, generated docs endpoint, Swagger UI, tests, and README usage notes.
- Placeholder scan: No placeholder tasks or undefined file paths remain.
- Type consistency: Test endpoint paths, metadata values, package names, and class names match the implementation code shown above.
