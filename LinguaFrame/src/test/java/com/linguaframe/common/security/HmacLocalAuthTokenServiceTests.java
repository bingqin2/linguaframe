package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.security.impl.HmacLocalAuthTokenService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacLocalAuthTokenServiceTests {

    private static final Instant NOW = Instant.parse("2026-06-28T12:00:00Z");
    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PASSWORD = "owner-password";

    @Test
    void issuesAndParsesConfiguredOwnerToken() {
        HmacLocalAuthTokenService service = new HmacLocalAuthTokenService(properties(10), fixedClock(NOW));

        String token = service.issueOwnerToken();
        LocalAuthTokenClaims claims = service.parse(token);

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(claims.username()).isEqualTo("owner");
        assertThat(claims.ownerId()).isEqualTo("owner-alpha");
        assertThat(claims.issuer()).isEqualTo("linguaframe-local");
        assertThat(claims.issuedAt()).isEqualTo(NOW);
        assertThat(claims.expiresAt()).isEqualTo(NOW.plusSeconds(600));
    }

    @Test
    void rejectsTamperedTokenWithoutLeakingSecrets() {
        HmacLocalAuthTokenService service = new HmacLocalAuthTokenService(properties(10), fixedClock(NOW));
        String token = service.issueOwnerToken();
        String tampered = token.substring(0, token.length() - 2) + "aa";

        assertThatThrownBy(() -> service.parse(tampered))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid local auth token")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void rejectsExpiredTokenWithoutLeakingSecrets() {
        HmacLocalAuthTokenService issuer = new HmacLocalAuthTokenService(properties(1), fixedClock(NOW));
        String token = issuer.issueOwnerToken();
        HmacLocalAuthTokenService verifier = new HmacLocalAuthTokenService(
                properties(1),
                fixedClock(NOW.plusSeconds(61))
        );

        assertThatThrownBy(() -> verifier.parse(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expired local auth token")
                .hasMessageNotContaining(SECRET)
                .hasMessageNotContaining(PASSWORD);
    }

    @Test
    void refusesToIssueTokenWhenLocalAuthIsNotConfigured() {
        LinguaFrameProperties properties = properties(10);
        properties.getAuth().setJwtSecret("short");
        HmacLocalAuthTokenService service = new HmacLocalAuthTokenService(properties, fixedClock(NOW));

        assertThatThrownBy(service::issueOwnerToken)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Local auth is not configured")
                .hasMessageNotContaining("short")
                .hasMessageNotContaining(PASSWORD);
    }

    private LinguaFrameProperties properties(int ttlMinutes) {
        LinguaFrameProperties properties = new LinguaFrameProperties();
        properties.getDemo().setOwnerId("owner-alpha");
        properties.getAuth().setEnabled(true);
        properties.getAuth().setOwnerUsername("owner");
        properties.getAuth().setOwnerPassword(PASSWORD);
        properties.getAuth().setJwtSecret(SECRET);
        properties.getAuth().setTokenTtlMinutes(ttlMinutes);
        properties.getAuth().setIssuer("linguaframe-local");
        return properties;
    }

    private Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
