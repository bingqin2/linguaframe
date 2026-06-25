# Harness Engineering

Harness engineering is the reliability layer around AI-assisted development. It makes LinguaFrame reproducible, testable, and debuggable.

## Goals

- Make local development repeatable.
- Make validation commands explicit.
- Make job progress auditable.
- Make provider and FFmpeg failures easy to investigate.
- Keep AI-generated changes within project guardrails.

## Required Harnesses

### Documentation Harness

Project documents live under:

```text
docs/product
docs/plans
docs/progress
docs/agent
```

Long-running implementation work should update:

```text
docs/progress/execution-log.md
```

### Test Harness

Every meaningful backend phase should define its required validation commands.

Common commands:

```bash
cd LinguaFrame
./mvnw test
```

Later phases may add:

```bash
docker compose config
docker compose up -d
curl -i http://localhost:8080/actuator/health
```

Frontend phases may add:

```bash
cd frontend
npm test
npm run build
```

### Media Harness

The final project should include:

- A small sample video under a documented test-data location or a script to generate one.
- Expected transcript, subtitle, and artifact outputs for smoke testing.
- A short successful demo path.
- A short failed-input demo path.

### Provider Harness

OpenAI calls should be isolated behind client interfaces so tests can use mocked responses.

Provider harness requirements:

- Mock speech-to-text response with timestamped segments.
- Mock translation response with structured segments.
- Mock TTS response with a small audio artifact fixture or fake object storage entry.
- Mock quality evaluation response with structured score and issue data.
- Usage metadata fixtures for cost calculation tests.
- Prompt-version fixtures for reproducibility tests.
- Cache-key fixtures for duplicate-work avoidance tests.

### Safety Harness

LinguaFrame must enforce:

- Upload size and duration limits.
- Supported media type checks.
- Object key safety.
- FFmpeg command templates.
- Retry limits.
- Secret redaction in logs.

### Demo Harness

The final project should include:

- A 30-60 second sample video.
- A repeatable local Docker Compose flow.
- A known successful localization job.
- A known failed job and retry example.
- Screenshots or generated artifacts for README evidence.

## Validation Records

Each implementation phase should record:

- Date.
- Command run.
- Result.
- Important failures.
- Follow-up decisions.

Use:

```text
docs/progress/execution-log.md
```
