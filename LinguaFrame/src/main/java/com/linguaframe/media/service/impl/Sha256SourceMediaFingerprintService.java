package com.linguaframe.media.service.impl;

import com.linguaframe.media.service.SourceMediaFingerprintService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class Sha256SourceMediaFingerprintService implements SourceMediaFingerprintService {

    private static final int BUFFER_SIZE = 8192;

    @Override
    public String sha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream inputStream = new DigestInputStream(file.getInputStream(), digest)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                while (inputStream.read(buffer) != -1) {
                    // DigestInputStream updates the digest as bytes are read.
                }
            }
            return toLowerHex(digest.digest());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read source video for fingerprinting.", ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", ex);
        }
    }

    private String toLowerHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
