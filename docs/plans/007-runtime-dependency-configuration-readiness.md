# Runtime Dependency Configuration Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add typed, validated configuration for MySQL, Redis, RabbitMQ, and MinIO, plus a read-only endpoint that exposes non-secret runtime dependency settings.

**Architecture:** Keep this slice configuration-only: no database driver, Redis client, RabbitMQ client, MinIO client, migrations, or network probes. Extend `LinguaFrameProperties` as the single typed configuration surface, then add a small service and controller that publish a sanitized summary for local demo readiness and future integration work.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring Web, Spring Validation, Spring Boot Test, AssertJ, Maven.

## Global Constraints

- Run all commands from the repository root.
- Use package `com.linguaframe.common.runtime` for the new runtime summary API.
- Keep all configuration under the `linguaframe` prefix.
- Do not expose passwords, access keys, secret keys, or API keys through any endpoint or log.
- Do not add MySQL, Redis, RabbitMQ, MinIO Java clients, Flyway, upload APIs, worker behavior, OpenAI, frontend, or FFmpeg in this slice.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
  - Responsibility: bind and validate media, worker, cost, database, redis, rabbitmq, and storage configuration.
- Modify: `LinguaFrame/src/main/resources/application.yaml`
  - Responsibility: provide local defaults for the new configuration groups.
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
  - Responsibility: keep local profile overrides aligned with root defaults.
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
  - Responsibility: bind Docker profile placeholders to Compose service names and environment variables.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/RuntimeDependencySummaryVo.java`
  - Responsibility: API response record for the sanitized dependency summary.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/NetworkDependencyVo.java`
  - Responsibility: host/port response record for MySQL, Redis, and RabbitMQ.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/StorageDependencyVo.java`
  - Responsibility: endpoint/bucket response record for object storage without credentials.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/RuntimeDependencySummaryService.java`
  - Responsibility: service contract for building the sanitized dependency summary.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
  - Responsibility: map `LinguaFrameProperties` into response objects without secrets.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/controller/RuntimeDependencyController.java`
  - Responsibility: expose `GET /api/runtime/dependencies`.
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
  - Responsibility: cover defaults and validation for the expanded config surface.
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
  - Responsibility: cover the runtime dependency endpoint and verify secrets are not returned.
- Modify: `README.md`
  - Responsibility: document the runtime dependency summary endpoint and new config keys.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record this feature slice and validation evidence.

## Task 1: Extend Typed Runtime Configuration

**Files:**
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`

**Interfaces:**
- Consumes: existing `LinguaFrameProperties#getMedia()`, `getWorker()`, and `getCost()`.
- Produces:
  - `LinguaFrameProperties#getDatabase(): Database`
  - `LinguaFrameProperties#getRedis(): Redis`
  - `LinguaFrameProperties#getRabbitmq(): Rabbitmq`
  - `LinguaFrameProperties#getStorage(): Storage`

- [x] **Step 1: Write failing default-binding assertions**

Add these assertions to `bindsDefaultRuntimeProperties()`:

```java
assertThat(properties.getDatabase().getHost()).isEqualTo("localhost");
assertThat(properties.getDatabase().getPort()).isEqualTo(3306);
assertThat(properties.getDatabase().getName()).isEqualTo("linguaframe");
assertThat(properties.getDatabase().getUsername()).isEqualTo("linguaframe");
assertThat(properties.getDatabase().getPassword()).isEqualTo("linguaframe_dev_password");
assertThat(properties.getRedis().getHost()).isEqualTo("localhost");
assertThat(properties.getRedis().getPort()).isEqualTo(6379);
assertThat(properties.getRabbitmq().getHost()).isEqualTo("localhost");
assertThat(properties.getRabbitmq().getPort()).isEqualTo(5672);
assertThat(properties.getRabbitmq().getUsername()).isEqualTo("linguaframe");
assertThat(properties.getRabbitmq().getPassword()).isEqualTo("linguaframe_dev_password");
assertThat(properties.getStorage().getEndpoint()).isEqualTo("http://localhost:9000");
assertThat(properties.getStorage().getBucket()).isEqualTo("linguaframe-artifacts");
assertThat(properties.getStorage().getAccessKey()).isEqualTo("linguaframe");
assertThat(properties.getStorage().getSecretKey()).isEqualTo("linguaframe_minio_password");
```

- [x] **Step 2: Run the focused test to verify RED**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
```

Expected: compilation fails with missing methods such as `getDatabase()`.

- [x] **Step 3: Add validation coverage for invalid dependency config**

Add this test method:

```java
@Test
void rejectsInvalidRuntimeDependencyProperties() {
    contextRunner
            .withPropertyValues(
                    "linguaframe.database.host=",
                    "linguaframe.database.port=0",
                    "linguaframe.database.name=",
                    "linguaframe.database.username=",
                    "linguaframe.database.password=",
                    "linguaframe.redis.host=",
                    "linguaframe.redis.port=70000",
                    "linguaframe.rabbitmq.host=",
                    "linguaframe.rabbitmq.port=0",
                    "linguaframe.rabbitmq.username=",
                    "linguaframe.rabbitmq.password=",
                    "linguaframe.storage.endpoint=",
                    "linguaframe.storage.bucket=",
                    "linguaframe.storage.access-key=",
                    "linguaframe.storage.secret-key="
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("linguaframe");
            });
}
```

- [x] **Step 4: Implement the new nested properties**

Update `LinguaFrameProperties.java` with four new `@Valid` fields and getters:

```java
@Valid
private final Database database = new Database();

@Valid
private final Redis redis = new Redis();

@Valid
private final Rabbitmq rabbitmq = new Rabbitmq();

@Valid
private final Storage storage = new Storage();

public Database getDatabase() {
    return database;
}

public Redis getRedis() {
    return redis;
}

public Rabbitmq getRabbitmq() {
    return rabbitmq;
}

public Storage getStorage() {
    return storage;
}
```

Import:

```java
import jakarta.validation.constraints.NotBlank;
```

Add these nested classes:

```java
public static class Database {

    @NotBlank
    private String host = "localhost";

    @Min(1)
    @Max(65535)
    private int port = 3306;

    @NotBlank
    private String name = "linguaframe";

    @NotBlank
    private String username = "linguaframe";

    @NotBlank
    private String password = "linguaframe_dev_password";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

public static class Redis {

    @NotBlank
    private String host = "localhost";

    @Min(1)
    @Max(65535)
    private int port = 6379;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

public static class Rabbitmq {

    @NotBlank
    private String host = "localhost";

    @Min(1)
    @Max(65535)
    private int port = 5672;

    @NotBlank
    private String username = "linguaframe";

    @NotBlank
    private String password = "linguaframe_dev_password";

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

public static class Storage {

    @NotBlank
    private String endpoint = "http://localhost:9000";

    @NotBlank
    private String bucket = "linguaframe-artifacts";

    @NotBlank
    private String accessKey = "linguaframe";

    @NotBlank
    private String secretKey = "linguaframe_minio_password";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
}
```

- [x] **Step 5: Add root and local config defaults**

Add to `application.yaml` and `application-local.yaml` under `linguaframe`:

```yaml
  database:
    host: ${MYSQL_HOST:localhost}
    port: ${MYSQL_PORT:3306}
    name: ${MYSQL_DATABASE:linguaframe}
    username: ${MYSQL_USER:linguaframe}
    password: ${MYSQL_PASSWORD:linguaframe_dev_password}
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:linguaframe}
    password: ${RABBITMQ_PASSWORD:linguaframe_dev_password}
  storage:
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
    bucket: ${MINIO_BUCKET:linguaframe-artifacts}
    access-key: ${MINIO_ROOT_USER:linguaframe}
    secret-key: ${MINIO_ROOT_PASSWORD:linguaframe_minio_password}
```

- [x] **Step 6: Align Docker profile with Compose service names**

Update `application-docker.yaml` so `linguaframe` contains:

```yaml
linguaframe:
  database:
    host: ${MYSQL_HOST:mysql}
    port: ${MYSQL_PORT:3306}
    name: ${MYSQL_DATABASE:linguaframe}
    username: ${MYSQL_USER:linguaframe}
    password: ${MYSQL_PASSWORD:linguaframe_dev_password}
  redis:
    host: ${REDIS_HOST:redis}
    port: ${REDIS_PORT:6379}
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:linguaframe}
    password: ${RABBITMQ_PASSWORD:linguaframe_dev_password}
  storage:
    endpoint: ${MINIO_ENDPOINT:http://minio:9000}
    bucket: ${MINIO_BUCKET:linguaframe-artifacts}
    access-key: ${MINIO_ROOT_USER:linguaframe}
    secret-key: ${MINIO_ROOT_PASSWORD:linguaframe_minio_password}
```

- [x] **Step 7: Verify properties tests pass**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
```

Expected: `Tests run: 3, Failures: 0, Errors: 0`.

## Task 2: Add Sanitized Runtime Dependency Summary API

**Files:**
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/RuntimeDependencySummaryVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/NetworkDependencyVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/domain/vo/StorageDependencyVo.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/RuntimeDependencySummaryService.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/service/impl/RuntimeDependencySummaryServiceImpl.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/runtime/controller/RuntimeDependencyController.java`

**Interfaces:**
- Consumes: `LinguaFrameProperties`.
- Produces: `GET /api/runtime/dependencies`.

- [x] **Step 1: Write failing endpoint test**

Create `RuntimeDependencyControllerTests.java`:

```java
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
```

- [x] **Step 2: Run endpoint test to verify RED**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RuntimeDependencyControllerTests
```

Expected: HTTP status assertion fails because `/api/runtime/dependencies` returns `404 NOT_FOUND`.

- [x] **Step 3: Add response records**

Create `NetworkDependencyVo.java`:

```java
package com.linguaframe.common.runtime.domain.vo;

public record NetworkDependencyVo(String type, String host, int port) {
}
```

Create `StorageDependencyVo.java`:

```java
package com.linguaframe.common.runtime.domain.vo;

public record StorageDependencyVo(String type, String endpoint, String bucket) {
}
```

Create `RuntimeDependencySummaryVo.java`:

```java
package com.linguaframe.common.runtime.domain.vo;

public record RuntimeDependencySummaryVo(
        NetworkDependencyVo database,
        NetworkDependencyVo redis,
        NetworkDependencyVo rabbitmq,
        StorageDependencyVo storage
) {
}
```

- [x] **Step 4: Add service contract and implementation**

Create `RuntimeDependencySummaryService.java`:

```java
package com.linguaframe.common.runtime.service;

import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;

public interface RuntimeDependencySummaryService {

    RuntimeDependencySummaryVo getSummary();
}
```

Create `RuntimeDependencySummaryServiceImpl.java`:

```java
package com.linguaframe.common.runtime.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.runtime.domain.vo.NetworkDependencyVo;
import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.domain.vo.StorageDependencyVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import org.springframework.stereotype.Service;

@Service
public class RuntimeDependencySummaryServiceImpl implements RuntimeDependencySummaryService {

    private final LinguaFrameProperties properties;

    public RuntimeDependencySummaryServiceImpl(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public RuntimeDependencySummaryVo getSummary() {
        return new RuntimeDependencySummaryVo(
                new NetworkDependencyVo(
                        "mysql",
                        properties.getDatabase().getHost(),
                        properties.getDatabase().getPort()
                ),
                new NetworkDependencyVo(
                        "redis",
                        properties.getRedis().getHost(),
                        properties.getRedis().getPort()
                ),
                new NetworkDependencyVo(
                        "rabbitmq",
                        properties.getRabbitmq().getHost(),
                        properties.getRabbitmq().getPort()
                ),
                new StorageDependencyVo(
                        "minio",
                        properties.getStorage().getEndpoint(),
                        properties.getStorage().getBucket()
                )
        );
    }
}
```

- [x] **Step 5: Add controller**

Create `RuntimeDependencyController.java`:

```java
package com.linguaframe.common.runtime.controller;

import com.linguaframe.common.runtime.domain.vo.RuntimeDependencySummaryVo;
import com.linguaframe.common.runtime.service.RuntimeDependencySummaryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime")
public class RuntimeDependencyController {

    private final RuntimeDependencySummaryService summaryService;

    public RuntimeDependencyController(RuntimeDependencySummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/dependencies")
    public RuntimeDependencySummaryVo getDependencies() {
        return summaryService.getSummary();
    }
}
```

- [x] **Step 6: Verify endpoint test passes**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RuntimeDependencyControllerTests
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

## Task 3: Document Runtime Dependency Readiness

**Files:**
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: expanded typed properties and runtime dependency summary API.
- Produces: contributor-facing command examples and validation evidence.

- [x] **Step 1: Update README runtime configuration section**

Add these keys to the existing runtime configuration list:

```markdown
- `linguaframe.database.host`
- `linguaframe.database.port`
- `linguaframe.database.name`
- `linguaframe.database.username`
- `linguaframe.database.password`
- `linguaframe.redis.host`
- `linguaframe.redis.port`
- `linguaframe.rabbitmq.host`
- `linguaframe.rabbitmq.port`
- `linguaframe.rabbitmq.username`
- `linguaframe.rabbitmq.password`
- `linguaframe.storage.endpoint`
- `linguaframe.storage.bucket`
- `linguaframe.storage.access-key`
- `linguaframe.storage.secret-key`
```

Add this short section after the API documentation section:

```markdown
## Runtime Dependency Summary

The backend exposes a non-secret dependency summary for local readiness checks:

```bash
curl http://localhost:8080/api/runtime/dependencies
```

The response includes MySQL, Redis, RabbitMQ, and MinIO host, port, endpoint, and bucket metadata. It intentionally excludes passwords, access keys, and secret keys.
```

- [x] **Step 2: Append execution log entry after final verification**

Append this entry to `docs/progress/execution-log.md` after successful validation:

```markdown
## 2026-06-25

Work:

- Added typed runtime dependency configuration for MySQL, Redis, RabbitMQ, and MinIO.
- Added a sanitized `GET /api/runtime/dependencies` endpoint.
- Added test coverage for dependency configuration validation and secret-free runtime summary output.
- Documented the new runtime configuration keys and summary endpoint.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` failed before implementation because dependency configuration getters did not exist.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RuntimeDependencyControllerTests` failed before implementation because `/api/runtime/dependencies` returned `404 NOT_FOUND`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=RuntimeDependencyControllerTests` passed.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with the final test count.

Notes:

- This slice intentionally did not open network connections to MySQL, Redis, RabbitMQ, or MinIO.
- The runtime summary endpoint must remain secret-free as more providers are added.
```

## Task 4: Final Verification And Commit

**Files:**
- Verify: all files touched by Tasks 1-3.

**Interfaces:**
- Consumes: completed runtime dependency configuration feature.
- Produces: one complete feature commit that will be merged back to `main` after verification.

- [x] **Step 1: Run full validation**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
docker compose --env-file .env.example config
docker compose --env-file .env.example build linguaframe-backend
```

Expected: Maven exits `0`, Compose config exits `0`, and backend image build exits `0`.

- [x] **Step 2: Review changed files**

Run:

```bash
git status --short
git diff -- LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java LinguaFrame/src/main/resources/application.yaml LinguaFrame/src/main/resources/application-local.yaml LinguaFrame/src/main/resources/application-docker.yaml LinguaFrame/src/main/java/com/linguaframe/common/runtime LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java README.md docs/progress/execution-log.md docs/plans/007-runtime-dependency-configuration-readiness.md
git diff --check
```

Expected: only this feature slice's files are modified or created, and `git diff --check` reports no whitespace errors.

- [x] **Step 3: Commit the feature**

Run:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java \
  LinguaFrame/src/main/resources/application.yaml \
  LinguaFrame/src/main/resources/application-local.yaml \
  LinguaFrame/src/main/resources/application-docker.yaml \
  LinguaFrame/src/main/java/com/linguaframe/common/runtime \
  LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java \
  LinguaFrame/src/test/java/com/linguaframe/common/runtime/RuntimeDependencyControllerTests.java \
  README.md \
  docs/progress/execution-log.md \
  docs/plans/007-runtime-dependency-configuration-readiness.md
git commit -m "Add runtime dependency configuration readiness"
```

Expected: one commit containing the complete feature.

- [x] **Step 4: Merge back to main**

After the feature branch commit is verified, run:

```bash
git switch main
git merge --ff-only runtime-dependency-configuration-readiness
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
git branch -d runtime-dependency-configuration-readiness
```

Expected: `main` contains the feature commit, Maven tests pass on `main`, and the feature branch is deleted.
