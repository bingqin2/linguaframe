# Root Maven Entrypoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a root-level Maven entrypoint so backend validation can be launched from the repository root while still compiling and testing the real `LinguaFrame/` module.

**Architecture:** Keep `LinguaFrame/pom.xml` as the backend module because it owns source, tests, resources, and Maven wrapper files. Add a root `pom.xml` with `packaging` set to `pom` and `LinguaFrame` listed as its only module. Root Maven commands delegate to the backend module instead of recreating the earlier no-op root build.

**Tech Stack:** Maven multi-module project, Java 21, Spring Boot 3.5.15, JUnit 5, Spring Boot Test.

## Global Constraints

- Do not move backend source files out of `LinguaFrame/src`.
- Do not remove `LinguaFrame/pom.xml`; it remains the backend module build file.
- Do not add product runtime features, Docker Compose, frontend code, or new dependencies in this slice.
- Do not put agent workflow preferences in `AGENTS.md`.
- Use `docs/progress/execution-log.md` for validation evidence.

---

## File Structure

- Create: `pom.xml`
  - Responsibility: repository-root Maven aggregator with `LinguaFrame` as a module.
- Modify: `README.md`
  - Responsibility: document root-level Maven commands and backend-module fallback commands.
- Modify: `AGENTS.md`
  - Responsibility: update contributor commands to prefer root Maven execution.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record root command validation.

## Task 1: Add Root Maven Aggregator

**Files:**
- Create: `pom.xml`
- Verify: `LinguaFrame/pom.xml`

**Interfaces:**
- Consumes: existing backend module at `LinguaFrame/pom.xml`.
- Produces: root command `mvn test` that runs the backend module tests.

- [ ] **Step 1: Confirm the current root command fails clearly**

Run:

```bash
mvn test
```

Expected: Maven fails at the repository root because no root `pom.xml` exists.

- [ ] **Step 2: Create root aggregator `pom.xml`**

Create:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.linguaframe</groupId>
    <artifactId>linguaframe-root</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>LinguaFrame Root</name>
    <description>Root Maven aggregator for LinguaFrame modules</description>

    <modules>
        <module>LinguaFrame</module>
    </modules>
</project>
```

- [ ] **Step 3: Verify Maven sees the backend module**

Run:

```bash
mvn -q help:evaluate -Dexpression=project.modules -DforceStdout
```

Expected output contains `LinguaFrame`.

## Task 2: Validate Root-Level Backend Test Execution

**Files:**
- Verify: `pom.xml`
- Verify: `LinguaFrame/src/test/java/com/linguaframe/LinguaFrameApplicationTests.java`

**Interfaces:**
- Consumes: root aggregator from Task 1.
- Produces: root-level validation command for future contributors.

- [ ] **Step 1: Run root test command**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: Maven builds root project, enters `LinguaFrame`, runs `LinguaFrameApplicationTests`, and reports `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 2: Run targeted module command**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test
```

Expected: Maven runs only the backend module and reports `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 3: Confirm root command is not no-op**

Inspect the root test output and confirm it includes:

```text
Building LinguaFrame
Running com.linguaframe.LinguaFrameApplicationTests
Tests run: 1, Failures: 0, Errors: 0
```

## Task 3: Update Commands in Docs

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: root commands validated in Task 2.
- Produces: contributor-facing instructions that prefer root commands.

- [ ] **Step 1: Update `README.md`**

Change the current foundation commands to:

```bash
mvn test
mvn -pl LinguaFrame spring-boot:run
```

Keep `cd LinguaFrame && ./mvnw test` as an optional backend-module fallback only if the root command is unavailable.

- [ ] **Step 2: Update `AGENTS.md`**

Replace the build command bullets with:

```markdown
- `mvn test` runs the repository Maven build and executes the backend Spring Boot test suite through the `LinguaFrame` module.
- `mvn -pl LinguaFrame spring-boot:run` starts the backend from the repository root.
- `find docs -maxdepth 2 -type f | sort` locates product, architecture, plan, and process references before implementation.
```

Do not add agent workflow rules to `AGENTS.md`.

- [ ] **Step 3: Re-read command docs**

Run:

```bash
sed -n '80,125p' README.md
sed -n '1,80p' AGENTS.md
```

Expected: root Maven commands are documented as the primary path.

## Task 4: Record Validation Evidence

**Files:**
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: validation outputs from Tasks 1-3.
- Produces: durable evidence that root commands now work.

- [ ] **Step 1: Append a dated execution entry**

Append:

```markdown
## 2026-06-25

Work:

- Added root Maven aggregator `pom.xml`.
- Kept `LinguaFrame/pom.xml` as the backend module build file.
- Updated README and AGENTS command examples to run Maven from the repository root.

Validation:

- `mvn test` failed before this slice because the repository root had no `pom.xml`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test` passed and ran `LinguaFrameApplicationTests`.
- `JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test` passed and ran the backend module test suite.

Notes:

- This slice restores root-level Maven commands without reintroducing the earlier no-op root build.
```

- [ ] **Step 2: Check final Maven file placement**

Run:

```bash
find . -maxdepth 2 -name pom.xml -print | sort
```

Expected:

```text
./LinguaFrame/pom.xml
./pom.xml
```

## Self-Review

- Spec coverage: The plan implements the requested root `pom.xml` and root command execution while preserving real backend test coverage.
- Placeholder scan: No placeholder steps or undefined follow-ups are included.
- Type consistency: No Java APIs are introduced; this slice changes Maven structure and documentation only.
