# Docker E2E Demo Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make LinguaFrame's current backend pipeline demonstrable from Docker with repeatable commands that prove upload, dispatch, worker execution, job polling, failure, and retry behavior.

**Architecture:** Keep this as a backend demo-readiness slice, not a real media-processing slice. Add scriptable demo tooling, a small test-only/demo-only failure hook for the smoke stage, and documentation that lets a reviewer run the Docker stack and inspect the resulting job lifecycle without reading internals.

**Tech Stack:** Java 21, Spring Boot 3.5.15, Spring AMQP, Spring JDBC, Flyway, MySQL, RabbitMQ, MinIO, Docker Compose, Bash, curl, Maven, JUnit 5, MockMvc, AssertJ.

## Global Constraints

- Run all commands from the repository root.
- Use feature branch `docker-e2e-demo-workflow`.
- Keep the feature focused on reproducible demo workflow and runtime verification.
- Do not add real FFmpeg, OpenAI, subtitle generation, TTS, frontend UI, authentication, Redis behavior, or external paid API calls.
- Do not commit generated media files, local `.env`, runtime volumes, credentials, or Docker data.
- Keep tests external-service-free; unit and controller tests must not require live Docker services.
- Any runtime demo script must fail clearly when Docker, curl, or required services are unavailable.
- Record final verification evidence in `docs/progress/execution-log.md`.

## Feature Boundary

This feature produces the following behavior:

- A committed demo script can create a tiny local video-like sample file, validate/upload it through the running backend, wait for the worker to complete the queued job, and print the job timeline.
- A committed failure/retry demo path can force the smoke worker stage to fail in Docker, verify that the job becomes `FAILED`, call retry, restore success mode, and verify completion after redispatch.
- Docker runtime configuration exposes only non-secret demo toggles needed for the smoke worker stage.
- Documentation tells a reviewer exactly how to start the stack, run success and retry demos, inspect API results, and clean up local runtime artifacts.
- Automated tests cover script-facing API assumptions and the demo failure hook without requiring Docker.

## Execution Notes

- Live Docker E2E exposed that RabbitMQ dispatch could not publish Java record payloads with the default `SimpleMessageConverter`; this slice includes a JSON message converter because the demo workflow cannot succeed without real queue delivery.
- Live Docker image verification exposed that container-internal Maven dependency resolution can fail when Maven Central is unreachable; this slice changes the backend image to copy the locally packaged Spring Boot jar so the demo build uses the already verified local Maven package step.

## File Structure

- Modify: `.dockerignore`
- Modify: `LinguaFrame/Dockerfile`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/config/RabbitJobQueueConfiguration.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerSmokePipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/config/RabbitJobQueueConfigurationTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`
- Create: `scripts/demo/docker-e2e-success.sh`
- Create: `scripts/demo/docker-e2e-retry.sh`
- Create: `scripts/demo/lib/linguaframe-demo.sh`
- Create: `docs/agent/docker-e2e-demo.md`
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`
- Modify: `docs/progress/execution-log.md`

## Task 1: Demo Failure Toggle For Smoke Worker

**Files:**
- Modify: `LinguaFrame/src/main/java/com/linguaframe/common/config/LinguaFrameProperties.java`
- Modify: `LinguaFrame/src/main/resources/application.yaml`
- Modify: `LinguaFrame/src/main/resources/application-local.yaml`
- Modify: `LinguaFrame/src/main/resources/application-docker.yaml`
- Modify: `LinguaFrame/src/test/resources/application-test.yaml`
- Modify: `.env.example`
- Modify: `docker-compose.yml`
- Modify: `LinguaFrame/src/main/java/com/linguaframe/job/service/impl/WorkerSmokePipelineStage.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/common/config/LinguaFramePropertiesTests.java`
- Modify: `LinguaFrame/src/test/java/com/linguaframe/job/service/LocalizationJobExecutionServiceTests.java`

**Interfaces:**
- `LinguaFrameProperties.Worker#isSmokeStageFailureEnabled()`
- `LinguaFrameProperties.Worker#setSmokeStageFailureEnabled(boolean smokeStageFailureEnabled)`

- [x] **Step 1: Confirm branch and baseline**

Run:

```bash
git status --short --branch
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: branch is `docker-e2e-demo-workflow`; tests pass before implementation.

- [x] **Step 2: Write failing property coverage**

Extend `LinguaFramePropertiesTests` with assertions:

```java
assertThat(properties.getWorker().isSmokeStageFailureEnabled()).isFalse();
```

Also bind a context with:

```properties
linguaframe.worker.smoke-stage-failure-enabled=true
```

Expected assertion:

```java
assertThat(properties.getWorker().isSmokeStageFailureEnabled()).isTrue();
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests
```

Expected: fail because the property does not exist.

- [x] **Step 3: Add the worker demo failure property**

Add to `LinguaFrameProperties.Worker`:

```java
private boolean smokeStageFailureEnabled = false;

public boolean isSmokeStageFailureEnabled() {
    return smokeStageFailureEnabled;
}

public void setSmokeStageFailureEnabled(boolean smokeStageFailureEnabled) {
    this.smokeStageFailureEnabled = smokeStageFailureEnabled;
}
```

- [x] **Step 4: Wire runtime configuration**

Add to runtime config:

```yaml
linguaframe:
  worker:
    smoke-stage-failure-enabled: ${LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED:false}
```

Add to `.env.example`:

```dotenv
LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=false
```

Add to the backend environment in `docker-compose.yml`:

```yaml
LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED: ${LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED:-false}
```

- [x] **Step 5: Make the smoke stage fail when enabled**

Update `WorkerSmokePipelineStage#execute` so it starts with:

```java
if (properties.getWorker().isSmokeStageFailureEnabled()) {
    throw new IllegalStateException("Demo smoke stage failure is enabled.");
}
```

Keep the existing duration sleep behavior unchanged when failure is disabled.

- [x] **Step 6: Cover failure behavior in execution service tests**

Add or extend a test in `LocalizationJobExecutionServiceTests` that injects a failing `LocalizationPipelineStage` and asserts:

```java
assertThat(result.status()).isEqualTo(LocalizationJobStatus.FAILED);
assertThat(job.failureStage()).isEqualTo(LocalizationJobStage.WORKER_SMOKE);
assertThat(job.failureReason()).contains("Demo smoke stage failure");
```

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests
```

Expected: pass.

## Task 2: Shared Demo Script Library

**Files:**
- Create: `scripts/demo/lib/linguaframe-demo.sh`

**Interfaces:**
- Bash functions: `require_command`, `demo_base_url`, `wait_for_backend`, `create_demo_sample`, `extract_json_field`, `upload_demo_video`, `wait_for_job_status`, `print_job_summary`

- [x] **Step 1: Create the script directory**

Run:

```bash
mkdir -p scripts/demo/lib
```

- [x] **Step 2: Add the shared Bash helpers**

Create `scripts/demo/lib/linguaframe-demo.sh`:

```bash
#!/usr/bin/env bash

set -euo pipefail

require_command() {
  local name="$1"
  if ! command -v "$name" >/dev/null 2>&1; then
    echo "Missing required command: $name" >&2
    exit 1
  fi
}

demo_base_url() {
  echo "${LINGUAFRAME_DEMO_BASE_URL:-http://localhost:8080}"
}

wait_for_backend() {
  local base_url="$1"
  local attempts="${2:-60}"
  local delay_seconds="${3:-2}"
  for ((i = 1; i <= attempts; i++)); do
    if curl -fsS "$base_url/actuator/health" | grep -q '"status":"UP"'; then
      return 0
    fi
    sleep "$delay_seconds"
  done
  echo "Backend did not become healthy at $base_url" >&2
  exit 1
}

create_demo_sample() {
  local path="$1"
  mkdir -p "$(dirname "$path")"
  printf 'linguaframe demo sample\n' > "$path"
}

extract_json_field() {
  local field="$1"
  python3 -c 'import json,sys; print(json.load(sys.stdin)[sys.argv[1]])' "$field"
}

upload_demo_video() {
  local base_url="$1"
  local sample_path="$2"
  curl -fsS \
    -F "file=@${sample_path};type=video/mp4" \
    -F "targetLanguage=zh-CN" \
    "$base_url/api/media/uploads"
}

wait_for_job_status() {
  local base_url="$1"
  local job_id="$2"
  local expected_status="$3"
  local attempts="${4:-60}"
  local delay_seconds="${5:-2}"
  local response
  local status
  for ((i = 1; i <= attempts; i++)); do
    response="$(curl -fsS "$base_url/api/jobs/$job_id")"
    status="$(printf '%s' "$response" | extract_json_field status)"
    if [[ "$status" == "$expected_status" ]]; then
      printf '%s\n' "$response"
      return 0
    fi
    sleep "$delay_seconds"
  done
  echo "Job $job_id did not reach $expected_status" >&2
  curl -fsS "$base_url/api/jobs/$job_id" >&2 || true
  exit 1
}

print_job_summary() {
  python3 -c '
import json, sys
job = json.load(sys.stdin)
print(f"jobId={job[\"jobId\"]}")
print(f"videoId={job[\"videoId\"]}")
print(f"status={job[\"status\"]}")
print(f"retryCount={job.get(\"retryCount\", 0)}")
for event in job.get("timelineEvents", []):
    print(f"- {event[\"stage\"]} {event[\"status\"]}: {event[\"message\"]}")
'
}
```

- [x] **Step 3: Make the helper executable and shell-check by running it through Bash syntax parsing**

Run:

```bash
chmod +x scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/lib/linguaframe-demo.sh
```

Expected: no output and exit code 0.

## Task 3: Successful Docker E2E Demo Script

**Files:**
- Create: `scripts/demo/docker-e2e-success.sh`

**Interfaces:**
- Uses `scripts/demo/lib/linguaframe-demo.sh`.
- Produces a console summary containing `status=COMPLETED`.

- [x] **Step 1: Add the success script**

Create `scripts/demo/docker-e2e-success.sh`:

```bash
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/sample.mp4}"

wait_for_backend "$BASE_URL"
create_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for job $job_id to complete..."
job_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
printf '%s' "$job_response" | print_job_summary
```

- [x] **Step 2: Make the script executable and syntax-check it**

Run:

```bash
chmod +x scripts/demo/docker-e2e-success.sh
bash -n scripts/demo/docker-e2e-success.sh
```

Expected: no output and exit code 0.

## Task 4: Failure And Retry Docker E2E Demo Script

**Files:**
- Create: `scripts/demo/docker-e2e-retry.sh`
- Modify: `docs/agent/docker-e2e-demo.md`

**Interfaces:**
- Uses `scripts/demo/lib/linguaframe-demo.sh`.
- Requires the operator to start Docker with `LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=true` for the first run.
- Produces console summaries containing `status=FAILED` and then `status=COMPLETED`.

- [x] **Step 1: Add the retry script**

Create `scripts/demo/docker-e2e-retry.sh`:

```bash
#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/linguaframe-demo.sh"

require_command curl
require_command python3

BASE_URL="$(demo_base_url)"
SAMPLE_PATH="${LINGUAFRAME_DEMO_SAMPLE_PATH:-/tmp/linguaframe-demo/retry-sample.mp4}"

wait_for_backend "$BASE_URL"
create_demo_sample "$SAMPLE_PATH"

upload_response="$(upload_demo_video "$BASE_URL" "$SAMPLE_PATH")"
job_id="$(printf '%s' "$upload_response" | extract_json_field jobId)"

echo "Uploaded demo video. Waiting for forced failure on job $job_id..."
failed_response="$(wait_for_job_status "$BASE_URL" "$job_id" FAILED)"
printf '%s' "$failed_response" | print_job_summary

echo
echo "Disable LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED, restart linguaframe-backend, then press Enter to retry."
read -r _

retry_response="$(curl -fsS -X POST "$BASE_URL/api/jobs/$job_id/retry")"
retry_status="$(printf '%s' "$retry_response" | extract_json_field status)"
if [[ "$retry_status" != "RETRYING" ]]; then
  echo "Expected retry API to return RETRYING, got $retry_status" >&2
  exit 1
fi

completed_response="$(wait_for_job_status "$BASE_URL" "$job_id" COMPLETED)"
printf '%s' "$completed_response" | print_job_summary
```

- [x] **Step 2: Make the script executable and syntax-check it**

Run:

```bash
chmod +x scripts/demo/docker-e2e-retry.sh
bash -n scripts/demo/docker-e2e-retry.sh
```

Expected: no output and exit code 0.

## Task 5: Demo Documentation And Smoke Checklist

**Files:**
- Create: `docs/agent/docker-e2e-demo.md`
- Modify: `README.md`
- Modify: `docs/agent/smoke-test-checklist.md`

**Interfaces:**
- Documents success command: `scripts/demo/docker-e2e-success.sh`
- Documents retry command: `scripts/demo/docker-e2e-retry.sh`

- [x] **Step 1: Write the Docker E2E demo guide**

Create `docs/agent/docker-e2e-demo.md` with:

```markdown
# Docker E2E Demo

This guide verifies the current LinguaFrame backend demo path: upload a small sample file, create a job, dispatch through RabbitMQ, execute the smoke worker stage, and inspect the job timeline.

## Start The Stack

```bash
docker compose --env-file .env.example up --build
```

Wait until the backend health endpoint is up:

```bash
curl http://localhost:8080/actuator/health
```

## Successful Job Demo

```bash
scripts/demo/docker-e2e-success.sh
```

Expected output includes:

```text
status=COMPLETED
- WORKER_RECEIVED STARTED
- WORKER_SMOKE STARTED
- WORKER_SMOKE SUCCEEDED
- COMPLETED SUCCEEDED
```

## Failure And Retry Demo

Start the stack with forced smoke-stage failure:

```bash
LINGUAFRAME_WORKER_SMOKE_STAGE_FAILURE_ENABLED=true docker compose --env-file .env.example up --build
```

Run:

```bash
scripts/demo/docker-e2e-retry.sh
```

When prompted, stop the backend, restart it with failure disabled, then press Enter:

```bash
docker compose --env-file .env.example up --build linguaframe-backend
```

Expected output first includes `status=FAILED`, then `status=COMPLETED`.

## Cleanup

```bash
docker compose --env-file .env.example down
```

Add `-v` only when you intentionally want to delete local MySQL, RabbitMQ, Redis, and MinIO volumes.
```

- [x] **Step 2: Link the demo guide from README**

Add a short `Docker E2E Demo` section to `README.md` that links to:

```markdown
docs/agent/docker-e2e-demo.md
```

Include the success command:

```bash
scripts/demo/docker-e2e-success.sh
```

- [x] **Step 3: Update smoke-test checklist**

Add checklist items for:

```markdown
- [ ] Docker stack starts with `docker compose --env-file .env.example up --build`.
- [ ] `scripts/demo/docker-e2e-success.sh` prints `status=COMPLETED`.
- [ ] Forced smoke-stage failure produces `status=FAILED`.
- [ ] Retry after disabling failure produces `status=COMPLETED`.
```

## Task 6: Verification And Progress Log

**Files:**
- Modify: `docs/progress/execution-log.md`

**Interfaces:**
- Final verification commands must be recorded exactly.

- [x] **Step 1: Run focused tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame test -Dtest=LinguaFramePropertiesTests,LocalizationJobExecutionServiceTests
```

Expected: pass.

- [x] **Step 2: Run full tests**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn test
```

Expected: pass.

- [x] **Step 3: Verify Docker configuration and backend image**

Run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example config
docker compose --env-file .env.example build linguaframe-backend
```

Expected: both pass.

- [x] **Step 4: Syntax-check demo scripts**

Run:

```bash
bash -n scripts/demo/lib/linguaframe-demo.sh
bash -n scripts/demo/docker-e2e-success.sh
bash -n scripts/demo/docker-e2e-retry.sh
```

Expected: all pass.

- [x] **Step 5: Optionally run live Docker E2E success**

If Docker is available and the stack can run locally, run:

```bash
JAVA_HOME=/Users/wangbingqin/Library/Java/JavaVirtualMachines/ms-21.0.11/Contents/Home mvn -pl LinguaFrame -am package -DskipTests
docker compose --env-file .env.example up --build
scripts/demo/docker-e2e-success.sh
```

Expected: script prints `status=COMPLETED`.

- [x] **Step 6: Record execution evidence**

Append a `2026-06-26` entry to `docs/progress/execution-log.md` with:

```markdown
Work:

- Added Docker E2E demo scripts for successful worker execution and forced failure/retry.
- Added a non-secret smoke-stage failure toggle for local demo verification.
- Documented the repeatable Docker demo workflow.

Validation:

- `<focused test command>` passed.
- `<full test command>` passed.
- `<docker compose config command>` passed.
- `<docker compose build command>` passed.
- `<bash syntax checks>` passed.
- `<live Docker E2E command>` passed, or `Not run` with the concrete reason.

Notes:

- This slice still does not run FFmpeg, OpenAI, subtitle generation, TTS, frontend UI, authentication, or Redis behavior.
```

- [x] **Step 7: Final whitespace check**

Run:

```bash
git diff --check
```

Expected: no output and exit code 0.
