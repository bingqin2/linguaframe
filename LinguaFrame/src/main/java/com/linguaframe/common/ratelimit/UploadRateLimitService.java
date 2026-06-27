package com.linguaframe.common.ratelimit;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.security.DemoAccessInterceptor;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class UploadRateLimitService {

    private static final String KEY_PREFIX = "linguaframe:rate-limit:upload:";

    private final LinguaFrameProperties properties;
    private final RateLimitCounterStore counterStore;
    private final Clock clock;

    public UploadRateLimitService(
            LinguaFrameProperties properties,
            RateLimitCounterStore counterStore,
            Clock clock
    ) {
        this.properties = properties;
        this.counterStore = counterStore;
        this.clock = clock;
    }

    public RateLimitDecision checkUploadAllowed(HttpServletRequest request) {
        LinguaFrameProperties.RateLimit rateLimit = properties.getRateLimit();
        int limit = rateLimit.getUploadMaxRequests();
        Window window = currentWindow(rateLimit.getUploadWindowSeconds());
        if (!rateLimit.isEnabled()) {
            return RateLimitDecision.allowed(limit, limit, window.resetAt(), window.retryAfterSeconds());
        }

        String key = KEY_PREFIX + hash(resolveClientIdentity(request)) + ":" + window.index();
        try {
            long count = counterStore.increment(key, Duration.ofSeconds(rateLimit.getUploadWindowSeconds() + 5L));
            int remaining = (int) Math.max(0L, limit - count);
            if (count > limit) {
                return RateLimitDecision.denied(limit, window.resetAt(), window.retryAfterSeconds());
            }
            return RateLimitDecision.allowed(limit, remaining, window.resetAt(), window.retryAfterSeconds());
        } catch (RuntimeException exception) {
            if (rateLimit.isFailOpen()) {
                return RateLimitDecision.allowed(limit, limit, window.resetAt(), window.retryAfterSeconds());
            }
            return RateLimitDecision.denied(limit, window.resetAt(), window.retryAfterSeconds());
        }
    }

    private Window currentWindow(int windowSeconds) {
        Instant now = clock.instant();
        long epochSecond = now.getEpochSecond();
        long index = epochSecond / windowSeconds;
        Instant resetAt = Instant.ofEpochSecond((index + 1L) * windowSeconds);
        long retryAfterSeconds = Math.max(0L, resetAt.getEpochSecond() - epochSecond);
        return new Window(index, resetAt, retryAfterSeconds);
    }

    private String resolveClientIdentity(HttpServletRequest request) {
        LinguaFrameProperties.Demo demo = properties.getDemo();
        String headerToken = request.getHeader(demo.getAccessHeaderName());
        if (demo.getAccessToken().equals(headerToken)) {
            return "demo-token:" + headerToken;
        }
        String cookieToken = readCookieToken(request);
        if (demo.getAccessToken().equals(cookieToken)) {
            return "demo-token:" + cookieToken;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return "ip:" + forwardedFor.split(",")[0].trim();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private String readCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (DemoAccessInterceptor.ACCESS_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record Window(long index, Instant resetAt, long retryAfterSeconds) {
    }
}
