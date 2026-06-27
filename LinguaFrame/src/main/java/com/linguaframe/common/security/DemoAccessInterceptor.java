package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class DemoAccessInterceptor implements HandlerInterceptor {

    public static final String ACCESS_COOKIE_NAME = "LinguaFrame-Demo-Token";

    private static final String ACCESS_REQUIRED_BODY = """
            {"error":"DEMO_ACCESS_REQUIRED","message":"Demo access token is required."}
            """;

    private final LinguaFrameProperties properties;

    public DemoAccessInterceptor(LinguaFrameProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        LinguaFrameProperties.Demo demo = properties.getDemo();
        if (!demo.isAccessGateEnabled()) {
            return true;
        }

        if (demo.getAccessToken().equals(request.getHeader(demo.getAccessHeaderName()))
                || demo.getAccessToken().equals(readCookieToken(request))) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(ACCESS_REQUIRED_BODY);
        return false;
    }

    private String readCookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (ACCESS_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
