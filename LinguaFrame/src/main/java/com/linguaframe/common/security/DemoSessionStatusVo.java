package com.linguaframe.common.security;

public record DemoSessionStatusVo(
        boolean accessGateEnabled,
        boolean authenticated,
        String headerName,
        String mode,
        String ownerId,
        String ownershipScope
) {
}
