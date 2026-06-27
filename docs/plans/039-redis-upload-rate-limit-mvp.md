# Redis Upload Rate Limit MVP Implementation Plan

## Goal

Add an optional Redis-backed upload rate limit for the demo upload APIs. This makes the existing Redis service part of the real backend path and adds a hosted-demo safety gate without introducing full user authentication or billing.

## Scope

- Limit only `POST /api/media/uploads` and `POST /api/media/uploads/validate`.
- Default the limiter to disabled so local demos keep current behavior.
- When enabled, count requests per short window using Redis and return structured `429 RATE_LIMIT_EXCEEDED` responses.
- Never store raw demo tokens, IP addresses, media paths, or uploaded content in Redis keys or logs.

## Implementation Steps

1. [x] Add rate-limit configuration.
   - Add `spring-boot-starter-data-redis`.
   - Wire `spring.data.redis.host` and `spring.data.redis.port` from existing `REDIS_HOST` and `REDIS_PORT`.
   - Add `linguaframe.rate-limit.enabled`, `upload-max-requests`, `upload-window-seconds`, and `fail-open` properties.
   - Extend `.env.example` and `docker-compose.yml` with matching environment variables.

2. [x] Add the rate-limit core.
   - Create a Redis counter boundary, for example `RateLimitCounterStore`, with a Redis implementation backed by `StringRedisTemplate`.
   - Create an upload limiter service that resolves a client identity from demo token, `X-Forwarded-For`, or remote address, hashes it, and uses fixed-window Redis keys such as `linguaframe:rate-limit:upload:<hash>:<window>`.
   - Support fail-open behavior when Redis is temporarily unavailable and the configured policy allows it.

3. [x] Protect upload endpoints.
   - Add a Spring MVC interceptor for the two upload `POST` routes.
   - On denial, return `429` JSON with `RATE_LIMIT_EXCEEDED`.
   - Set useful headers such as `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, and `Retry-After`.

4. [x] Cover behavior with tests.
   - Test property binding and defaults.
   - Test allowed, denied, fail-open, and fail-closed limiter behavior with a fake counter store.
   - Test interceptor responses and headers.
   - Keep existing upload controller tests passing with the limiter disabled by default.

5. [x] Update documentation and progress logs.
   - Document the environment variables and upload rate-limit behavior in `README.md`.
   - Update product roadmap/spec notes that the Redis rate-limit hook is implemented for upload APIs.
   - Record decisions and validation evidence in `docs/progress/`.

## Validation

- `mvn -pl LinguaFrame -Dtest='*RateLimit*Tests,MediaUploadControllerTests,LinguaFramePropertiesTests' test`
- `mvn -pl LinguaFrame test`
- `docker compose --env-file .env config`
- `git diff --check`

## Completion Criteria

- Upload rate limiting is configurable, disabled by default, and active when explicitly enabled.
- Denied requests receive deterministic `429` responses without storing sensitive raw identifiers.
- Redis failures do not break local demos when `fail-open=true`.
- The feature is committed on a feature branch and merged back to `main` after validation.
