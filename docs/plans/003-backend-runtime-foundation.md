# Backend Runtime Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the backend from a minimal Spring Boot shell into a locally runnable service with web, validation, actuator health, and profile-ready configuration.

**Architecture:** Keep `LinguaFrame/` as the single backend module inside the root Maven aggregator. Add only runtime foundation dependencies and configuration needed for a health-checkable service. Use Spring Boot Actuator `/actuator/health` instead of a custom controller so later Docker and operations work can reuse the standard health endpoint.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Maven multi-module build, Spring Web, Spring Validation, Spring Boot Actuator, JUnit 5, Spring Boot Test.

## Global Constraints

- Run Maven commands from the repository root.
- Keep backend packages under `com.linguaframe`.
- Do not add database, Redis, RabbitMQ, MinIO, OpenAI, Docker, frontend, upload, worker, or FFmpeg behavior in this slice.
- Do not commit secrets or real environment-specific values.
- Do not put agent workflow preferences in `AGENTS.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## File Structure

- Modify: `LinguaFrame/pom.xml`
  - Responsibility: add web, validation, and actuator dependencies.
- Modify: `LinguaFrame/src/main/resources/application.yaml`
  - Responsibility: configure application name, actuator health exposure, and `linguaframe` defaults.
- Create: `LinguaFrame/src/main/resources/application-local.yaml`
  - Responsibility: local profile placeholders and logging defaults.
- Verify: `LinguaFrame/src/test/java/com/linguaframe/LinguaFrameApplicationTests.java`
  - Responsibility: keep context smoke coverage.
- Create: `LinguaFrame/src/test/java/com/linguaframe/actuator/ActuatorHealthTests.java`
  - Responsibility: prove `/actuator/health` is available over HTTP.
- Modify: `README.md`
  - Responsibility: document root runtime commands and health check URL.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record this feature slice and validation commands.

## Task 1: Add a Failing Health Endpoint Test

**Files:**
- Create: `LinguaFrame/src/test/java/com/linguaframe/actuator/ActuatorHealthTests.java`

**Interfaces:**
- Consumes: existing `LinguaFrameApplication`.
- Produces: a test class proving `GET /actuator/health` returns HTTP 200 and `status: UP`.

- [ ] **Step 1: Create the failing test**

Create `LinguaFrame/src/test/java/com/linguaframe/actuator/ActuatorHealthTests.java`:

```java
package com.linguaframe.actuator;

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
class ActuatorHealthTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void actuatorHealthReturnsUp() {
        ResponseEntity<HealthResponse> response = restTemplate.getForEntity(
                "http://localhost:" + port + "/actuator/health",
                HealthResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("UP");
    }

    private record HealthResponse(String status, Map<String, Object> components) {
    }
}
```

- [ ] **Step 2: Verify the test fails before runtime dependencies exist**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=ActuatorHealthTests
```

Expected: compilation fails because `TestRestTemplate`, `LocalServerPort`, web test support, or actuator endpoint support is not available with the current starter set.

## Task 2: Add Runtime Foundation Dependencies

**Files:**
- Modify: `LinguaFrame/pom.xml`

**Interfaces:**
- Consumes: failing `ActuatorHealthTests`.
- Produces: a backend module with Web MVC, Bean Validation, and Actuator available.

- [ ] **Step 1: Add dependencies**

Update `<dependencies>` in `LinguaFrame/pom.xml` so it contains:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Run the health test**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=ActuatorHealthTests
```

Expected: test passes with `Tests run: 1, Failures: 0, Errors: 0`.

## Task 3: Add Runtime Configuration

**Files:**
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Create: `LinguaFrame/src/main/resources/application-local.yaml`

**Interfaces:**
- Consumes: Spring Boot configuration conventions and `linguaframe` prefix from `docs/product/backend-code-standard.md`.
- Produces: profile-ready configuration without secrets.

- [ ] **Step 1: Replace `application.yaml`**

Set `LinguaFrame/src/main/resources/application.yaml` to:

```yaml
spring:
  application:
    name: LinguaFrame

management:
  endpoints:
    web:
      exposure:
        include: health,info
  endpoint:
    health:
      probes:
        enabled: true

linguaframe:
  media:
    max-file-size-mb: 100
    max-duration-seconds: 120
  worker:
    max-retries: 2
    stage-timeout-seconds: 600
  cost:
    enabled: true
```

- [ ] **Step 2: Add local profile config**

Create `LinguaFrame/src/main/resources/application-local.yaml`:

```yaml
logging:
  level:
    com.linguaframe: DEBUG

linguaframe:
  media:
    max-file-size-mb: 100
    max-duration-seconds: 120
```

- [ ] **Step 3: Run the full backend test suite**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: root Maven build runs `LinguaFrameApplicationTests` and `ActuatorHealthTests`, with `Tests run: 2, Failures: 0, Errors: 0`.

## Task 4: Document Runtime Commands

**Files:**
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: validated root commands from Task 3.
- Produces: docs that explain how to test, run, and check health locally.

- [ ] **Step 1: Update README commands**

In `README.md`, update the current foundation commands to include:

```bash
mvn test
mvn -pl LinguaFrame spring-boot:run
curl http://localhost:8080/actuator/health
```

Add one short sentence after the command block:

```markdown
The health endpoint should return a JSON body with `"status":"UP"` once the backend is running.
```

- [ ] **Step 2: Append execution-log entry**

Append to `docs/progress/execution-log.md`:

```markdown
## 2026-06-25

Work:

- Added backend runtime foundation dependencies for Web, Validation, and Actuator.
- Added actuator health coverage through `ActuatorHealthTests`.
- Added base and local Spring configuration using the `linguaframe` prefix.
- Documented root-level test, run, and health-check commands.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=ActuatorHealthTests` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 2, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add database, queue, object storage, OpenAI, Docker, frontend, upload, or worker behavior.
```

- [ ] **Step 3: Final verification**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: root Maven build passes and reports both backend tests passing.

## Self-Review

- Spec coverage: This plan advances the basic version by adding the web runtime and standard health endpoint required before upload, Docker, and infrastructure integration.
- Placeholder scan: No placeholder steps or undefined follow-ups are included.
- Type consistency: The only new Java class is `ActuatorHealthTests`, and all referenced Spring test types are introduced by the planned dependency changes.
