package com.linguaframe.common.security;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class AuthenticatedOwnerContext {

    private LocalAuthTokenClaims claims;

    public LocalAuthTokenClaims claims() {
        return claims;
    }

    public void authenticate(LocalAuthTokenClaims claims) {
        this.claims = claims;
    }

    public void clear() {
        this.claims = null;
    }
}
