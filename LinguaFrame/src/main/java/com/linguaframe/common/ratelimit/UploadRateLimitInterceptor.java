package com.linguaframe.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class UploadRateLimitInterceptor implements HandlerInterceptor {

    private static final String RATE_LIMIT_EXCEEDED_BODY = """
            {"error":"RATE_LIMIT_EXCEEDED","message":"Upload rate limit exceeded. Try again later."}
            """;

    private final UploadRateLimitService uploadRateLimitService;

    public UploadRateLimitInterceptor(UploadRateLimitService uploadRateLimitService) {
        this.uploadRateLimitService = uploadRateLimitService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        RateLimitDecision decision = uploadRateLimitService.checkUploadAllowed(request);
        writeHeaders(response, decision);
        if (decision.allowed()) {
            return true;
        }
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(RATE_LIMIT_EXCEEDED_BODY);
        return false;
    }

    private void writeHeaders(HttpServletResponse response, RateLimitDecision decision) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", decision.resetAt().toString());
        if (!decision.allowed()) {
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
        }
    }
}
