package com.linguaframe.common.security.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.common.security.LocalAuthTokenClaims;
import com.linguaframe.common.security.LocalAuthTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HmacLocalAuthTokenService implements LocalAuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final LinguaFrameProperties properties;
    private final Clock clock;

    @Autowired
    public HmacLocalAuthTokenService(LinguaFrameProperties properties) {
        this(properties, Clock.systemUTC());
    }

    public HmacLocalAuthTokenService(LinguaFrameProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String issueOwnerToken() {
        LinguaFrameProperties.Auth auth = properties.getAuth();
        if (!auth.isLocalAuthConfigured()) {
            throw new IllegalStateException("Local auth is not configured.");
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(auth.getTokenTtlMinutes() * 60L);
        String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
        String payload = encodeJson(orderedPayload(
                auth.getIssuer(),
                auth.getOwnerUsername(),
                properties.getDemo().getOwnerId(),
                issuedAt,
                expiresAt
        ));
        String signingInput = header + "." + payload;
        return signingInput + "." + sign(signingInput);
    }

    @Override
    public LocalAuthTokenClaims parse(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }

        String signingInput = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                sign(signingInput).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }

        String payloadJson = new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        String issuer = readStringClaim(payloadJson, "iss");
        String username = readStringClaim(payloadJson, "sub");
        String ownerId = readStringClaim(payloadJson, "ownerId");
        Instant issuedAt = Instant.ofEpochSecond(readLongClaim(payloadJson, "iat"));
        Instant expiresAt = Instant.ofEpochSecond(readLongClaim(payloadJson, "exp"));

        if (!properties.getAuth().getIssuer().equals(issuer) || username.isBlank() || ownerId.isBlank()) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }
        if (!expiresAt.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Expired local auth token.");
        }

        return new LocalAuthTokenClaims(username, ownerId, issuer, issuedAt, expiresAt);
    }

    private Map<String, Object> orderedPayload(
            String issuer,
            String username,
            String ownerId,
            Instant issuedAt,
            Instant expiresAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("iss", issuer);
        payload.put("sub", username);
        payload.put("ownerId", ownerId);
        payload.put("iat", issuedAt.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());
        return payload;
    }

    private String encodeJson(Map<String, ?> values) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number number) {
                builder.append(number);
            } else {
                builder.append('"').append(escape(String.valueOf(value))).append('"');
            }
        }
        builder.append('}');
        return ENCODER.encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.getAuth().getJwtSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign local auth token.", exception);
        }
    }

    private String readStringClaim(String json, String claim) {
        String marker = "\"" + claim + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }
        int valueStart = start + marker.length();
        int valueEnd = json.indexOf('"', valueStart);
        if (valueEnd < 0) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }
        return json.substring(valueStart, valueEnd);
    }

    private long readLongClaim(String json, String claim) {
        String marker = "\"" + claim + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }
        int valueStart = start + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }
        if (valueEnd == valueStart) {
            throw new IllegalArgumentException("Invalid local auth token.");
        }
        return Long.parseLong(json.substring(valueStart, valueEnd));
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
