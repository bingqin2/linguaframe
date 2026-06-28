package com.linguaframe.common.security;

import java.time.Instant;

public record LocalAuthTokenClaims(
        String username,
        String ownerId,
        String issuer,
        Instant issuedAt,
        Instant expiresAt
) {
}
