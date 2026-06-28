package com.linguaframe.common.security;

public record AuthSessionStatusVo(
        boolean enabled,
        boolean configured,
        boolean authenticated,
        String ownerId,
        String username,
        String ownershipScope,
        String authMode
) {
}
