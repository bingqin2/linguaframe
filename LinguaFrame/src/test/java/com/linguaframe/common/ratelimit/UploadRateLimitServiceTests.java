package com.linguaframe.common.ratelimit;

import com.linguaframe.common.config.LinguaFrameProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UploadRateLimitServiceTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-27T03:00:05Z"), ZoneOffset.UTC);

    @Test
    void allowsRequestsWhenLimiterIsDisabledWithoutTouchingStore() {
        LinguaFrameProperties properties = properties(false, true);
        RecordingRateLimitCounterStore store = new RecordingRateLimitCounterStore();
        UploadRateLimitService service = new UploadRateLimitService(properties, store, clock);

        RateLimitDecision decision = service.checkUploadAllowed(requestWithRemoteAddress("203.0.113.10"));

        assertThat(decision.allowed()).isTrue();
        assertThat(store.calls()).isEmpty();
    }

    @Test
    void allowsWithinLimitAndDeniesAfterLimit() {
        LinguaFrameProperties properties = properties(true, true);
        properties.getRateLimit().setUploadMaxRequests(2);
        RecordingRateLimitCounterStore store = new RecordingRateLimitCounterStore();
        store.returnCounts(1, 2, 3);
        UploadRateLimitService service = new UploadRateLimitService(properties, store, clock);

        RateLimitDecision first = service.checkUploadAllowed(requestWithRemoteAddress("203.0.113.10"));
        RateLimitDecision second = service.checkUploadAllowed(requestWithRemoteAddress("203.0.113.10"));
        RateLimitDecision third = service.checkUploadAllowed(requestWithRemoteAddress("203.0.113.10"));

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.remaining()).isZero();
        assertThat(third.limit()).isEqualTo(2);
        assertThat(third.retryAfterSeconds()).isEqualTo(55);
        assertThat(third.resetAt()).isEqualTo(Instant.parse("2026-06-27T03:01:00Z"));
    }

    @Test
    void hashesClientIdentityBeforeWritingRedisKey() {
        LinguaFrameProperties properties = properties(true, true);
        properties.getDemo().setAccessToken("secret-demo-token");
        RecordingRateLimitCounterStore store = new RecordingRateLimitCounterStore();
        store.returnCounts(1);
        UploadRateLimitService service = new UploadRateLimitService(properties, store, clock);
        MockHttpServletRequest request = requestWithRemoteAddress("203.0.113.10");
        request.addHeader(properties.getDemo().getAccessHeaderName(), "secret-demo-token");

        service.checkUploadAllowed(request);

        assertThat(store.calls()).hasSize(1);
        String key = store.calls().getFirst().key();
        assertThat(key).startsWith("linguaframe:rate-limit:upload:");
        assertThat(key).doesNotContain("secret-demo-token");
        assertThat(key).doesNotContain("203.0.113.10");
    }

    @Test
    void canUseDemoAccessCookieAsClientIdentity() {
        LinguaFrameProperties properties = properties(true, true);
        properties.getDemo().setAccessToken("cookie-demo-token");
        RecordingRateLimitCounterStore store = new RecordingRateLimitCounterStore();
        store.returnCounts(1);
        UploadRateLimitService service = new UploadRateLimitService(properties, store, clock);
        MockHttpServletRequest request = requestWithRemoteAddress("203.0.113.10");
        request.setCookies(new Cookie("LinguaFrame-Demo-Token", "cookie-demo-token"));

        service.checkUploadAllowed(request);

        assertThat(store.calls()).hasSize(1);
        assertThat(store.calls().getFirst().key()).doesNotContain("cookie-demo-token");
    }

    @Test
    void allowsRequestWhenStoreFailsAndFailOpenIsEnabled() {
        LinguaFrameProperties properties = properties(true, true);
        RecordingRateLimitCounterStore store = new RecordingRateLimitCounterStore();
        store.failWith(new IllegalStateException("redis unavailable"));
        UploadRateLimitService service = new UploadRateLimitService(properties, store, clock);

        RateLimitDecision decision = service.checkUploadAllowed(requestWithRemoteAddress("203.0.113.10"));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(properties.getRateLimit().getUploadMaxRequests());
    }

    @Test
    void deniesRequestWhenStoreFailsAndFailOpenIsDisabled() {
        LinguaFrameProperties properties = properties(true, false);
        RecordingRateLimitCounterStore store = new RecordingRateLimitCounterStore();
        store.failWith(new IllegalStateException("redis unavailable"));
        UploadRateLimitService service = new UploadRateLimitService(properties, store, clock);

        RateLimitDecision decision = service.checkUploadAllowed(requestWithRemoteAddress("203.0.113.10"));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.remaining()).isZero();
    }

    private LinguaFrameProperties properties(boolean enabled, boolean failOpen) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getRateLimit().setEnabled(enabled);
        properties.getRateLimit().setUploadMaxRequests(20);
        properties.getRateLimit().setUploadWindowSeconds(60);
        properties.getRateLimit().setFailOpen(failOpen);
        return properties;
    }

    private MockHttpServletRequest requestWithRemoteAddress(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private static final class RecordingRateLimitCounterStore implements RateLimitCounterStore {

        private final List<CounterCall> calls = new ArrayList<>();
        private final List<Long> counts = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public long increment(String key, Duration ttl) {
            calls.add(new CounterCall(key, ttl));
            if (failure != null) {
                throw failure;
            }
            if (counts.isEmpty()) {
                return 1;
            }
            return counts.removeFirst();
        }

        void returnCounts(long... values) {
            for (long value : values) {
                counts.add(value);
            }
        }

        void failWith(RuntimeException failure) {
            this.failure = failure;
        }

        List<CounterCall> calls() {
            return calls;
        }
    }

    private record CounterCall(String key, Duration ttl) {
    }
}
