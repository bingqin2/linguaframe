package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TranscriptionCacheLookupBo;
import com.linguaframe.job.service.TranscriptionCacheKeyService;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class TranscriptionCacheKeyServiceImpl implements TranscriptionCacheKeyService {

    @Override
    public TranscriptionCacheLookupBo build(String provider, String model, String promptVersion, byte[] audioContent) {
        String normalizedProvider = requireText(provider, "provider");
        String normalizedModel = requireText(model, "model");
        String normalizedPromptVersion = requireText(promptVersion, "prompt version");
        if (audioContent == null || audioContent.length == 0) {
            throw new IllegalArgumentException("Transcription cache audio must not be empty.");
        }

        String audioHash = sha256(audioContent);
        String cacheKey = sha256(String.join("\n",
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion,
                audioHash
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new TranscriptionCacheLookupBo(
                cacheKey,
                audioHash,
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion
        );
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Transcription cache " + label + " must not be blank.");
        }
        return value.trim();
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
