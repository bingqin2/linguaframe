# Repository Guidelines

## Project Structure & Module Organization

LinguaFrame is a Spring Boot foundation for an AI video localization platform. Backend source lives under `LinguaFrame/src/main/java/com/linguaframe`, tests under `LinguaFrame/src/test/java/com/linguaframe`, and configuration under `LinguaFrame/src/main/resources/application.yaml`. Product, architecture, roadmap, plans, and agent notes live in `docs/`. The top-level `frontend/` directory is currently empty; React + Vite + TypeScript and Docker Compose runtime files are planned.

## Build, Test, and Development Commands

- `mvn test` runs the repository Maven build and executes the backend Spring Boot test suite through the `LinguaFrame` module.
- `mvn -pl LinguaFrame spring-boot:run` starts the backend from the repository root.
- `find docs -maxdepth 2 -type f | sort` locates product, architecture, plan, and process references before implementation.

## Coding Style & Naming Conventions

Use Java 21 and Spring Boot conventions. Keep package names under `com.linguaframe`. Follow `docs/product/backend-code-standard.md`: controllers handle HTTP only, services own business transitions, repositories or mappers own persistence, and external systems belong behind client/provider interfaces. Prefer names such as `CreateLocalizationJobDto`, `LocalizationJobDetailVo`, and `OpenAiSpeechClient` over vague `Request`, `Response`, or `Manager` classes. Use constructor injection with `private final` dependencies.

## Testing Guidelines

Use JUnit 5 and Spring Boot Test. Place tests beside the backend module under `LinguaFrame/src/test/java`. Name test classes after the unit or behavior under test, for example `LocalizationJobServiceTests`. Every non-trivial change should include either a focused unit test or a documented validation command in `docs/progress/execution-log.md`.

## Commit & Pull Request Guidelines

This workspace is not currently initialized as a Git repository, so no local commit convention is available. Use concise imperative commit subjects such as `Add localization job model` or `Document backend package rules`. Pull requests should include a short purpose statement, validation evidence, and linked issue or plan file when applicable.

## Agent-Specific Instructions

Inspect the repository before editing, follow the docs-driven roadmap, and record meaningful implementation decisions in `docs/progress/decisions.md`. Never claim success without running validation, and never log or expose OpenAI keys, object storage credentials, or raw user-supplied media paths.
