package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo-session")
@Tag(name = "Demo Session", description = "Private demo owner-session gate status and controls.")
public class DemoSessionController {

    private final LinguaFrameProperties properties;
    private final DemoOwnerIdentityService ownerIdentityService;

    public DemoSessionController(LinguaFrameProperties properties, DemoOwnerIdentityService ownerIdentityService) {
        this.properties = properties;
        this.ownerIdentityService = ownerIdentityService;
    }

    @GetMapping
    @Operation(summary = "Read private demo session status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sanitized demo session status returned")
    })
    public DemoSessionStatusVo getSession(HttpServletRequest request) {
        return status(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Create a private demo owner session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner session created"),
            @ApiResponse(responseCode = "401", description = "Configured demo token was not provided")
    })
    public ResponseEntity<DemoSessionStatusVo> login(
            @RequestBody DemoSessionLoginRequestDto request,
            HttpServletResponse response
    ) {
        if (!properties.getDemo().isAccessGateEnabled()) {
            return ResponseEntity.ok(openStatus());
        }

        if (!isConfiguredToken(request == null ? null : request.token())) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                    .body(requiredStatus());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie(properties.getDemo().getAccessToken()).toString());
        return ResponseEntity.ok(activeStatus());
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the private demo owner session")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Owner session cleared")
    })
    public DemoSessionStatusVo logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, clearSessionCookie().toString());
        if (!properties.getDemo().isAccessGateEnabled()) {
            return openStatus();
        }
        return requiredStatus();
    }

    private DemoSessionStatusVo status(HttpServletRequest request) {
        if (!properties.getDemo().isAccessGateEnabled()) {
            return openStatus();
        }
        if (isAuthenticated(request)) {
            return activeStatus();
        }
        return requiredStatus();
    }

    private DemoSessionStatusVo openStatus() {
        return status("OPEN", false, true);
    }

    private DemoSessionStatusVo activeStatus() {
        return status("OWNER_SESSION_ACTIVE", true, true);
    }

    private DemoSessionStatusVo requiredStatus() {
        return status("OWNER_SESSION_REQUIRED", true, false);
    }

    private DemoSessionStatusVo status(String mode, boolean accessGateEnabled, boolean authenticated) {
        return new DemoSessionStatusVo(
                accessGateEnabled,
                authenticated,
                properties.getDemo().getAccessHeaderName(),
                mode,
                ownerIdentityService.currentOwnerId(),
                ownerIdentityService.ownershipScope()
        );
    }

    private boolean isAuthenticated(HttpServletRequest request) {
        LinguaFrameProperties.Demo demo = properties.getDemo();
        return isConfiguredToken(request.getHeader(demo.getAccessHeaderName()))
                || isConfiguredToken(readCookieToken(request));
    }

    private boolean isConfiguredToken(String token) {
        return properties.getDemo().isAccessGateEnabled()
                && properties.getDemo().getAccessToken().equals(token);
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

    private ResponseCookie sessionCookie(String token) {
        return ResponseCookie.from(DemoAccessInterceptor.ACCESS_COOKIE_NAME, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .build();
    }

    private ResponseCookie clearSessionCookie() {
        return ResponseCookie.from(DemoAccessInterceptor.ACCESS_COOKIE_NAME, "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
    }

    public record DemoSessionLoginRequestDto(String token) {
    }
}
