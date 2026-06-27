package com.linguaframe.common.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UploadRateLimitInterceptorTests {

    @Test
    void allowsRequestWhenDecisionAllows() throws Exception {
        UploadRateLimitService service = mock(UploadRateLimitService.class);
        when(service.checkUploadAllowed(any()))
                .thenReturn(RateLimitDecision.allowed(20, 19, Instant.parse("2026-06-27T03:01:00Z"), 55));
        UploadRateLimitInterceptor interceptor = new UploadRateLimitInterceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/uploads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("19");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("2026-06-27T03:01:00Z");
    }

    @Test
    void ignoresNonPostRequests() throws Exception {
        UploadRateLimitService service = mock(UploadRateLimitService.class);
        UploadRateLimitInterceptor interceptor = new UploadRateLimitInterceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/media/uploads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        verifyNoInteractions(service);
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }

    @Test
    void deniesRequestWithStructuredJsonAndRetryHeaders() throws Exception {
        UploadRateLimitService service = mock(UploadRateLimitService.class);
        when(service.checkUploadAllowed(any()))
                .thenReturn(RateLimitDecision.denied(20, Instant.parse("2026-06-27T03:01:00Z"), 55));
        UploadRateLimitInterceptor interceptor = new UploadRateLimitInterceptor(service);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/media/uploads");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isFalse();
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("20");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(response.getHeader("X-RateLimit-Reset")).isEqualTo("2026-06-27T03:01:00Z");
        assertThat(response.getHeader("Retry-After")).isEqualTo("55");
    }
}
