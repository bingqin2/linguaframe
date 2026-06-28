package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class LocalAuthInterceptor implements HandlerInterceptor {

    private static final String AUTH_REQUIRED_BODY = """
            {"error":"LOCAL_AUTH_REQUIRED","message":"Local account authentication is required."}
            """;

    private final LinguaFrameProperties properties;
    private final LocalAuthTokenService tokenService;
    private final AuthenticatedOwnerContext ownerContext;

    public LocalAuthInterceptor(
            LinguaFrameProperties properties,
            LocalAuthTokenService tokenService,
            AuthenticatedOwnerContext ownerContext
    ) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.ownerContext = ownerContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        ownerContext.clear();
        if (!properties.getAuth().isLocalAuthConfigured()) {
            return true;
        }
        if (hasDemoAccess(request)) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            try {
                ownerContext.authenticate(tokenService.parse(authorization.substring("Bearer ".length()).trim()));
                return true;
            } catch (IllegalArgumentException ignored) {
                // Fall through to a sanitized 401.
            }
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(AUTH_REQUIRED_BODY);
        return false;
    }

    private boolean hasDemoAccess(HttpServletRequest request) {
        LinguaFrameProperties.Demo demo = properties.getDemo();
        if (!demo.isAccessGateEnabled()) {
            return false;
        }
        return demo.getAccessToken().equals(request.getHeader(demo.getAccessHeaderName()))
                || demo.getAccessToken().equals(readCookieToken(request));
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
}
