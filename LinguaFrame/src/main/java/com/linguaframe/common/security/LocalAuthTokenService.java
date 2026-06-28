package com.linguaframe.common.security;

public interface LocalAuthTokenService {

    String issueOwnerToken();

    LocalAuthTokenClaims parse(String token);
}
