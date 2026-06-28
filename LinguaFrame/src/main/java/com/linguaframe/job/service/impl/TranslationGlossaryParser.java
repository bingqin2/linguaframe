package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.TranslationGlossaryBo;
import com.linguaframe.job.domain.bo.TranslationGlossaryEntryBo;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class TranslationGlossaryParser {

    private static final int MAX_RAW_LENGTH = 2_000;
    private static final int MAX_ENTRIES = 20;
    private static final int MAX_TERM_LENGTH = 80;

    private final ObjectMapper objectMapper;

    public TranslationGlossaryParser() {
        this(new ObjectMapper());
    }

    public TranslationGlossaryParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TranslationGlossaryBo parse(String rawGlossary) {
        if (rawGlossary == null || rawGlossary.isBlank()) {
            return TranslationGlossaryBo.empty();
        }
        if (rawGlossary.length() > MAX_RAW_LENGTH) {
            throw new IllegalArgumentException("Translation glossary must be 2000 characters or fewer.");
        }

        List<TranslationGlossaryEntryBo> entries = new ArrayList<>();
        String[] lines = rawGlossary.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (line.isEmpty()) {
                continue;
            }
            entries.add(parseLine(line, index + 1));
            if (entries.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("Translation glossary supports at most 20 entries.");
            }
        }
        if (entries.isEmpty()) {
            return TranslationGlossaryBo.empty();
        }
        String json = toJson(entries);
        return new TranslationGlossaryBo(List.copyOf(entries), json, sha256(json), entries.size());
    }

    public TranslationGlossaryBo fromStoredJson(String json, String hash, int entryCount) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) {
            return TranslationGlossaryBo.empty();
        }
        try {
            List<TranslationGlossaryEntryBo> entries = objectMapper.readValue(
                    json,
                    new TypeReference<List<TranslationGlossaryEntryBo>>() {
                    }
            );
            String normalizedHash = hash == null ? "" : hash.trim();
            return new TranslationGlossaryBo(List.copyOf(entries), json, normalizedHash, entryCount);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored translation glossary JSON was invalid.", ex);
        }
    }

    private TranslationGlossaryEntryBo parseLine(String line, int lineNumber) {
        int separatorIndex = line.indexOf("=>");
        int separatorLength = 2;
        if (separatorIndex < 0) {
            separatorIndex = line.indexOf('=');
            separatorLength = 1;
        }
        if (separatorIndex < 0) {
            throw new IllegalArgumentException("Translation glossary entry must use '=>' or '=' on line " + lineNumber + ".");
        }
        String sourceTerm = line.substring(0, separatorIndex).trim();
        String targetTerm = line.substring(separatorIndex + separatorLength).trim();
        if (sourceTerm.isEmpty()) {
            throw new IllegalArgumentException("Translation glossary source term must not be blank on line " + lineNumber + ".");
        }
        if (targetTerm.isEmpty()) {
            throw new IllegalArgumentException("Translation glossary target term must not be blank on line " + lineNumber + ".");
        }
        if (sourceTerm.length() > MAX_TERM_LENGTH || targetTerm.length() > MAX_TERM_LENGTH) {
            throw new IllegalArgumentException("Translation glossary terms must be 80 characters or fewer.");
        }
        return new TranslationGlossaryEntryBo(sourceTerm, targetTerm);
    }

    private String toJson(List<TranslationGlossaryEntryBo> entries) {
        try {
            return objectMapper.writeValueAsString(entries);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Translation glossary could not be serialized.", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }
}
