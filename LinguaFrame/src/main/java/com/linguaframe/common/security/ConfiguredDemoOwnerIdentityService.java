package com.linguaframe.common.security;

import com.linguaframe.common.config.LinguaFrameProperties;
import org.springframework.stereotype.Service;

@Service
public class ConfiguredDemoOwnerIdentityService implements DemoOwnerIdentityService {

    private final LinguaFrameProperties properties;
    private final AuthenticatedOwnerContext ownerContext;

    public ConfiguredDemoOwnerIdentityService(
            LinguaFrameProperties properties,
            AuthenticatedOwnerContext ownerContext
    ) {
        this.properties = properties;
        this.ownerContext = ownerContext;
    }

    @Override
    public String currentOwnerId() {
        LocalAuthTokenClaims claims = ownerContext.claims();
        if (claims != null) {
            return claims.ownerId();
        }
        return properties.getDemo().getOwnerId();
    }

    @Override
    public String ownershipScope() {
        if (ownerContext.claims() != null) {
            return "LOCAL_AUTH_OWNER";
        }
        return DemoOwnerIdentityService.super.ownershipScope();
    }
}
