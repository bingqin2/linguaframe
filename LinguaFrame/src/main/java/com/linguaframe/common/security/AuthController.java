package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.time.Instant;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Local Account Auth", description = "Minimal local account authentication for owner-operated demos.")
public class AuthController {

    private final LinguaFrameProperties properties;
    private final DemoOwnerIdentityService ownerIdentityService;
    private final LocalAuthTokenService tokenService;
    private final AuthenticatedOwnerContext ownerContext;

    public AuthController(
            LinguaFrameProperties properties,
            DemoOwnerIdentityService ownerIdentityService,
            LocalAuthTokenService tokenService,
            AuthenticatedOwnerContext ownerContext
    ) {
        this.properties = properties;
        this.ownerIdentityService = ownerIdentityService;
        this.tokenService = tokenService;
        this.ownerContext = ownerContext;
    }

    @GetMapping("/session")
    @Operation(summary = "Read local account auth session status")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sanitized auth session status returned")
    })
    public AuthSessionStatusVo session(HttpServletRequest request) {
        LocalAuthTokenClaims claims = parseBearer(request);
        if (claims != null) {
            return activeStatus(claims);
        }
        return unauthenticatedStatus();
    }

    @PostMapping("/login")
    @Operation(summary = "Create a local account bearer token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bearer token created"),
            @ApiResponse(responseCode = "401", description = "Credentials were rejected"),
            @ApiResponse(responseCode = "503", description = "Local auth is disabled or unconfigured")
    })
    public ResponseEntity<?> login(@RequestBody(required = false) AuthLoginRequestDto request) {
        LinguaFrameProperties.Auth auth = properties.getAuth();
        if (!auth.isLocalAuthConfigured()) {
            return ResponseEntity.status(HttpServletResponse.SC_SERVICE_UNAVAILABLE)
                    .body(unauthenticatedStatus());
        }
        if (!matches(auth.getOwnerUsername(), request == null ? null : request.username())
                || !matches(auth.getOwnerPassword(), request == null ? null : request.password())) {
            return ResponseEntity.status(HttpServletResponse.SC_UNAUTHORIZED)
                    .body(new AuthLoginResponseVo(null, "Bearer", null, unauthenticatedStatus()));
        }

        String token = tokenService.issueOwnerToken();
        LocalAuthTokenClaims claims = tokenService.parse(token);
        return ResponseEntity.ok(new AuthLoginResponseVo(token, "Bearer", claims.expiresAt(), activeStatus(claims)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Clear the client-side local account bearer token")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stateless logout status returned")
    })
    public AuthSessionStatusVo logout() {
        ownerContext.clear();
        return unauthenticatedStatus();
    }

    private AuthSessionStatusVo unauthenticatedStatus() {
        LinguaFrameProperties.Auth auth = properties.getAuth();
        String mode = auth.isLocalAuthConfigured() ? "LOCAL_AUTH_REQUIRED" : "LOCAL_AUTH_DISABLED";
        return new AuthSessionStatusVo(
                auth.isEnabled(),
                auth.isLocalAuthConfigured(),
                false,
                ownerIdentityService.currentOwnerId(),
                auth.getOwnerUsername(),
                mode
        );
    }

    private AuthSessionStatusVo activeStatus(LocalAuthTokenClaims claims) {
        return new AuthSessionStatusVo(
                properties.getAuth().isEnabled(),
                properties.getAuth().isLocalAuthConfigured(),
                true,
                claims.ownerId(),
                claims.username(),
                "LOCAL_AUTH_ACTIVE"
        );
    }

    private LocalAuthTokenClaims parseBearer(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            LocalAuthTokenClaims claims = tokenService.parse(authorization.substring("Bearer ".length()).trim());
            ownerContext.authenticate(claims);
            return claims;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean matches(String expected, String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(), candidate.getBytes());
    }
}
