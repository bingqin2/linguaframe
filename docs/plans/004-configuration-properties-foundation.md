# Configuration Properties Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add typed and validated runtime configuration for media limits, worker execution bounds, and cost tracking.

**Architecture:** Bind the existing `linguaframe` YAML section into a single Spring Boot configuration properties class. Register configuration properties scanning at the application entry point, validate numeric bounds with Bean Validation, and cover both successful binding and invalid configuration startup failure with tests.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Maven, Spring Boot Configuration Properties, Jakarta Bean Validation, JUnit 5, AssertJ.

## Global Constraints

- Run Maven commands from the repository root.
- Keep backend packages under `com.linguaframe`.
- Use the `linguaframe` prefix for all LinguaFrame-specific configuration.
- Do not add database, Redis, RabbitMQ, MinIO, OpenAI, Docker, frontend, upload, worker, or FFmpeg behavior in this slice.
- Do not commit secrets or real environment-specific values.
- Do not put agent workflow preferences in `AGENTS.md`.
- Record validation evidence in `docs/progress/execution-log.md`.

---

## File Structure

- Modify: `LinguaFrame/src/main/java/com/linguaframe/LinguaFrameApplication.java`
  - Responsibility: enable scanning for `@ConfigurationProperties` classes under `com.linguaframe`.
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
  - Responsibility: expose typed `media`, `worker`, and `cost` runtime settings with validation bounds.
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
  - Responsibility: prove YAML defaults bind correctly and invalid values fail startup.
- Modify: `README.md`
  - Responsibility: document the current runtime configuration surface.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record this feature slice and validation commands.

## Task 1: Add Failing Configuration Properties Tests

**Files:**
- Create: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`

**Interfaces:**
- Consumes: existing `LinguaFrame/src/main/resources/application.yaml` values under `linguaframe`.
- Produces: tests that require a `com.linguaframe.common.config.LinguaFrameProperties` bean.

- [ ] **Step 1: Create the failing test**

Create `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`:

```java
package com.linguaframe.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LinguaFramePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesTestConfiguration.class);

    @Autowired
    private LinguaFrameProperties properties;

    @Test
    void bindsDefaultRuntimeProperties() {
        assertThat(properties.getMedia().getMaxFileSizeMb()).isEqualTo(100);
        assertThat(properties.getMedia().getMaxDurationSeconds()).isEqualTo(120);
        assertThat(properties.getWorker().getMaxRetries()).isEqualTo(2);
        assertThat(properties.getWorker().getStageTimeoutSeconds()).isEqualTo(600);
        assertThat(properties.getCost().isEnabled()).isTrue();
    }

    @Test
    void rejectsInvalidRuntimeProperties() {
        contextRunner
                .withPropertyValues(
                        "linguaframe.media.max-file-size-mb=0",
                        "linguaframe.worker.max-retries=-1",
                        "linguaframe.worker.stage-timeout-seconds=0"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("linguaframe");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(LinguaFrameProperties.class)
    static class PropertiesTestConfiguration {
    }
}
```

- [ ] **Step 2: Verify the test fails before implementation**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
```

Expected: compilation fails with `cannot find symbol` for `LinguaFrameProperties`.

## Task 2: Implement Typed Runtime Configuration

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/LinguaFrameApplication.java`
- Create: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`

**Interfaces:**
- Consumes: `linguaframe.media`, `linguaframe.worker`, and `linguaframe.cost` YAML keys.
- Produces: `LinguaFrameProperties` bean with nested `Media`, `Worker`, and `Cost` property groups.

- [ ] **Step 1: Enable configuration properties scanning**

Set `LinguaFrame/src/main/java/com/linguaframe/LinguaFrameApplication.java` to:

```java
package com.linguaframe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LinguaFrameApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinguaFrameApplication.class, args);
    }

}
```

- [ ] **Step 2: Add the properties class**

Create `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`:

```java
package com.linguaframe.common.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "linguaframe")
public class LinguaFrameProperties {

    @Valid
    private final Media media = new Media();

    @Valid
    private final Worker worker = new Worker();

    @Valid
    private final Cost cost = new Cost();

    public Media getMedia() {
        return media;
    }

    public Worker getWorker() {
        return worker;
    }

    public Cost getCost() {
        return cost;
    }

    public static class Media {

        @Min(1)
        @Max(10240)
        private int maxFileSizeMb = 100;

        @Min(1)
        @Max(86400)
        private int maxDurationSeconds = 120;

        public int getMaxFileSizeMb() {
            return maxFileSizeMb;
        }

        public void setMaxFileSizeMb(int maxFileSizeMb) {
            this.maxFileSizeMb = maxFileSizeMb;
        }

        public int getMaxDurationSeconds() {
            return maxDurationSeconds;
        }

        public void setMaxDurationSeconds(int maxDurationSeconds) {
            this.maxDurationSeconds = maxDurationSeconds;
        }
    }

    public static class Worker {

        @Min(0)
        @Max(10)
        private int maxRetries = 2;

        @Min(1)
        @Max(86400)
        private int stageTimeoutSeconds = 600;

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public int getStageTimeoutSeconds() {
            return stageTimeoutSeconds;
        }

        public void setStageTimeoutSeconds(int stageTimeoutSeconds) {
            this.stageTimeoutSeconds = stageTimeoutSeconds;
        }
    }

    public static class Cost {

        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
```

- [ ] **Step 3: Verify the properties tests pass**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

## Task 3: Document The Runtime Configuration Surface

**Files:**
- Modify: `README.md`
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: `LinguaFrameProperties` public getters and current YAML keys.
- Produces: contributor-facing documentation for current runtime config defaults.

- [ ] **Step 1: Add README configuration notes**

Insert this section after `Current Foundation Commands` in `README.md`:

```markdown
## Runtime Configuration

Default backend configuration lives in `LinguaFrame/src/main/resources/application.yaml`. Local development overrides live in `LinguaFrame/src/main/resources/application-local.yaml`.

The current `linguaframe` configuration surface is bound to `LinguaFrameProperties`:

- `linguaframe.media.max-file-size-mb`
- `linguaframe.media.max-duration-seconds`
- `linguaframe.worker.max-retries`
- `linguaframe.worker.stage-timeout-seconds`
- `linguaframe.cost.enabled`

Do not commit API keys, storage credentials, database passwords, or provider secrets.
```

- [ ] **Step 2: Add execution log entry**

Append this entry to `docs/progress/execution-log.md` after successful verification:

```markdown
## 2026-06-25

Work:

- Added typed `LinguaFrameProperties` binding for media, worker, and cost configuration.
- Enabled configuration properties scanning from the Spring Boot application entry point.
- Added validation coverage for invalid numeric runtime settings.
- Documented the current runtime configuration surface in `README.md`.

Validation:

- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` failed before implementation with `cannot find symbol` for `LinguaFrameProperties`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests` passed with `Tests run: 2, Failures: 0, Errors: 0`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed with `Tests run: 4, Failures: 0, Errors: 0`.

Notes:

- This slice intentionally did not add external service dependencies or upload/worker behavior.
- Future upload validation should consume `LinguaFrameProperties.Media` instead of duplicating media limits.
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

Expected: root Maven build passes with `Tests run: 4, Failures: 0, Errors: 0`.

- [ ] **Step 2: Review changed files**

Run:

```bash
git status --short
git diff -- LinguaFrame/src/main/java/com/linguaframe/LinguaFrameApplication.java LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java README.md docs/progress/execution-log.md docs/plans/004-configuration-properties-foundation.md
```

Expected: only this feature slice's files are modified or created.

- [ ] **Step 3: Commit the feature**

Run:

```bash
git add LinguaFrame/src/main/java/com/linguaframe/LinguaFrameApplication.java \
  LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java \
  LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java \
  README.md \
  docs/progress/execution-log.md \
  docs/plans/004-configuration-properties-foundation.md
git commit -m "Add typed runtime configuration properties"
```

Expected: one commit containing the complete configuration properties foundation feature.

## Self-Review

- Spec coverage: This plan advances the basic version by formalizing the existing `linguaframe` configuration prefix and making media, worker, and cost settings available to upcoming upload and processing features.
- Placeholder scan: No placeholder tasks or undefined file paths remain.
- Type consistency: Test names, package names, getters, and nested property class names match the implementation code shown above.
