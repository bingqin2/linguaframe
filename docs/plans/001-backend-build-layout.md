# Backend Build Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the backend Maven build compile and test the actual Spring Boot source tree under `LinguaFrame/src`.

**Architecture:** Treat `LinguaFrame/` as the backend Maven module because it already owns the Maven wrapper, `.mvn/`, source tree, test tree, and application resources. Move the active Maven project metadata into that module, remove the misleading root-level build entry point, then align README and execution logs with the verified commands.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Maven Wrapper, JUnit 5, Spring Boot Test.

## Global Constraints

- Keep package names under `com.linguaframe`.
- Do not add product features, infrastructure dependencies, Docker files, or frontend code in this slice.
- Do not store agent workflow preferences in `AGENTS.md`.
- Use `docs/progress/execution-log.md` for validation evidence.
- This repository root is not currently a Git repository, so do not plan git commits as required validation.

---

## File Structure

- Move: `pom.xml` -> `LinguaFrame/pom.xml`
  - Responsibility: define the backend Maven project beside its wrapper and `src` tree.
- Modify: `README.md`
  - Responsibility: document the corrected backend commands and remove no-op root Maven guidance.
- Modify: `AGENTS.md`
  - Responsibility: update contributor commands so future agents run real backend validation.
- Modify: `docs/progress/execution-log.md`
  - Responsibility: record the build-layout correction and exact validation output.

## Task 1: Put the Maven Project Beside the Backend Sources

**Files:**
- Create: `LinguaFrame/pom.xml`
- Delete: `pom.xml`
- Test: `LinguaFrame/src/test/java/com/linguaframe/LinguaFrameApplicationTests.java`

**Interfaces:**
- Consumes: existing Maven wrapper at `LinguaFrame/mvnw`.
- Produces: a backend build command, `cd LinguaFrame && ./mvnw test`, that compiles `src/main/java` and runs `src/test/java`.

- [ ] **Step 1: Confirm the current failure mode**

Run:

```bash
./LinguaFrame/mvnw -f pom.xml test
```

Expected: build success with messages like `No sources to compile` and `No tests to run`, proving this command does not validate the backend source tree.

- [ ] **Step 2: Move the Maven project file into the backend module**

Move the root `pom.xml` to:

```text
LinguaFrame/pom.xml
```

Do not change dependency versions or Java version in this task.

- [ ] **Step 3: Run the backend test command**

Run:

```bash
cd LinguaFrame
./mvnw test
```

Expected: Maven compiles `LinguaFrameApplication.java`, compiles `LinguaFrameApplicationTests.java`, and reports `Tests run: 1, Failures: 0, Errors: 0`.

## Task 2: Align Repository Guidance With the Real Build

**Files:**
- Modify: `README.md`
- Modify: `AGENTS.md`

**Interfaces:**
- Consumes: verified command from Task 1, `cd LinguaFrame && ./mvnw test`.
- Produces: contributor-facing instructions that no longer document no-op root Maven commands.

- [ ] **Step 1: Update `README.md` commands**

Replace the current backend command block with:

```bash
cd LinguaFrame
./mvnw test
./mvnw spring-boot:run
```

Keep the note that Docker Compose, frontend, and `.env.example` are planned foundation work until those files exist.

- [ ] **Step 2: Update `AGENTS.md` commands**

Replace the current build command bullets with:

```markdown
- `cd LinguaFrame && ./mvnw test` compiles the backend and runs the Spring Boot test suite.
- `cd LinguaFrame && ./mvnw spring-boot:run` starts the backend locally.
- `find docs -maxdepth 2 -type f | sort` locates product, architecture, plan, and process references before implementation.
```

Do not add agent workflow rules to `AGENTS.md`.

- [ ] **Step 3: Re-read both docs**

Run:

```bash
sed -n '1,180p' README.md
sed -n '1,180p' AGENTS.md
```

Expected: both files refer to the backend Maven project through `cd LinguaFrame`, and neither claims the root Maven command is the normal validation path.

## Task 3: Record Validation Evidence

**Files:**
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Consumes: test output from Task 1 and documentation checks from Task 2.
- Produces: a durable progress entry for the first completed feature slice.

- [ ] **Step 1: Append a dated execution entry**

Append:

```markdown
## 2026-06-25

Work:

- Completed backend build-layout correction.
- Moved the Maven project file into `LinguaFrame/` so the wrapper, `pom.xml`, source tree, test tree, and resources live in the same backend module.
- Updated contributor-facing commands in `README.md` and `AGENTS.md`.

Validation:

- `./LinguaFrame/mvnw -f pom.xml test` before the fix showed `No sources to compile` and `No tests to run`.
- `cd LinguaFrame && ./mvnw test` after the fix compiled backend sources and ran `LinguaFrameApplicationTests`.

Notes:

- This slice intentionally did not add dependencies, Docker Compose, frontend code, or application features.
```

- [ ] **Step 2: Run final validation**

Run:

```bash
cd LinguaFrame
./mvnw test
```

Expected: `BUILD SUCCESS` and `Tests run: 1, Failures: 0, Errors: 0`.

- [ ] **Step 3: Check final file placement**

Run:

```bash
find . -maxdepth 2 -name pom.xml -print
```

Expected:

```text
./LinguaFrame/pom.xml
```

## Self-Review

- Spec coverage: This plan implements the first complete feature slice needed for reliable future work: backend build validation now targets real source and tests.
- Placeholder scan: No placeholder steps or undefined follow-ups are included.
- Type consistency: No new Java APIs are introduced; existing application and test class names remain unchanged.
