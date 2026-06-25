# Basic Version Profile

## Purpose

The basic version exists to make LinguaFrame runnable, testable, and ready for the first upload and processing features. It is not the full video localization product.

## Included In The Basic Version

- Product documentation.
- One Spring Boot backend module.
- Java 21 baseline.
- Maven build.
- Spring Web dependency.
- Spring Validation dependency.
- Spring Boot Actuator dependency.
- MySQL driver dependency.
- Flyway dependency.
- Redis dependency.
- RabbitMQ dependency.
- MinIO client dependency.
- OpenAPI dependency.
- Local and docker Spring profiles.
- A small health endpoint or actuator health.
- A minimal Dockerfile for the backend.
- Docker Compose skeleton for MySQL, Redis, RabbitMQ, MinIO, and backend.
- Root-level README explaining the target system.

## Not Included In The Basic Version

- React UI implementation.
- Full JWT authentication.
- Actual video upload.
- FFmpeg command execution.
- OpenAI API calls.
- Subtitle export.
- TTS generation.
- Subtitle-burned video generation.
- Cost calculation.
- Retry behavior.

## Foundation Commands

Run from the backend directory until a root Maven aggregator exists:

```bash
cd LinguaFrame
./mvnw test
./mvnw spring-boot:run
```

After root-level runtime files are added, run from repository root:

```bash
mvn test
docker compose config
docker compose build linguaframe-backend
```

## Expected Runtime Shape

```text
React frontend later
      |
LinguaFrame backend
      |
MySQL + Redis + RabbitMQ + MinIO
```

The backend starts as one deployable Spring Boot service. API/worker split should happen only after the upload-to-artifact pipeline is proven.
