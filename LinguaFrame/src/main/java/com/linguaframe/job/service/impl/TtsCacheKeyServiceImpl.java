package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TtsCacheLookupBo;
import com.linguaframe.job.service.TtsCacheKeyService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Service
public class TtsCacheKeyServiceImpl implements TtsCacheKeyService {

    private static final Pattern BLANK_LINES = Pattern.compile("\\R\\s*\\R+");

    @Override
    public TtsCacheLookupBo build(String language, String provider, String model, String voice, String text) {
        String normalizedLanguage = requireText(language, "language");
        String normalizedProvider = requireText(provider, "provider");
        String normalizedModel = requireText(model, "model");
        String normalizedVoice = requireText(voice, "voice");
        String normalizedText = normalizeText(text);

        String textHash = sha256(normalizedText);
        String cacheKey = sha256(String.join("\n",
                normalizedProvider,
                normalizedModel,
                normalizedVoice,
                normalizedLanguage,
                textHash
        ));
        return new TtsCacheLookupBo(
                cacheKey,
                textHash,
                normalizedLanguage,
                normalizedProvider,
                normalizedModel,
                normalizedVoice
        );
    }

    private String normalizeText(String value) {
        String text = requireText(value, "text");
        String withoutBlankLines = BLANK_LINES.matcher(text).replaceAll("\n");
        return withoutBlankLines.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow(() -> new IllegalArgumentException("TTS cache text must not be blank."));
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TTS cache " + label + " must not be blank.");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
