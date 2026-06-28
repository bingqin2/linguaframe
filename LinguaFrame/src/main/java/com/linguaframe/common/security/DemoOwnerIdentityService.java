package com.linguaframe.common.security;

public interface DemoOwnerIdentityService {

    String currentOwnerId();

    default String ownershipScope() {
        return "CONFIGURED_DEMO_OWNER";
    }
}
