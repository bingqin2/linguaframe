package com.linguaframe.common.security;

import java.time.Instant;

public record AuthLoginResponseVo(
        String token,
        String tokenType,
        Instant expiresAt,
        AuthSessionStatusVo session
) {
}
