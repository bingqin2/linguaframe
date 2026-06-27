package com.linguaframe.common.ratelimit;

import java.time.Instant;

public record RateLimitDecision(
        boolean allowed,
        int limit,
        int remaining,
        Instant resetAt,
        long retryAfterSeconds
) {

    public static RateLimitDecision allowed(int limit, int remaining, Instant resetAt, long retryAfterSeconds) {
        return new RateLimitDecision(true, limit, Math.max(0, remaining), resetAt, Math.max(0L, retryAfterSeconds));
    }

    public static RateLimitDecision denied(int limit, Instant resetAt, long retryAfterSeconds) {
        return new RateLimitDecision(false, limit, 0, resetAt, Math.max(0L, retryAfterSeconds));
    }
}
