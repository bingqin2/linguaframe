package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.TranslationCacheLookupBo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.TranslationCacheKeyService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
public class TranslationCacheKeyServiceImpl implements TranslationCacheKeyService {

    private final ObjectMapper objectMapper;

    public TranslationCacheKeyServiceImpl() {
        this(new ObjectMapper());
    }

    public TranslationCacheKeyServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public TranslationCacheLookupBo build(
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            String translationStyle,
            String translationGlossaryHash,
            List<TranscriptSegmentVo> segments
    ) {
        String normalizedTargetLanguage = requireText(targetLanguage, "target language");
        String normalizedProvider = requireText(provider, "provider");
        String normalizedModel = requireText(model, "model");
        String normalizedPromptVersion = requireText(promptVersion, "prompt version");
        String normalizedTranslationStyle = requireText(translationStyle, "translation style").toUpperCase();
        String normalizedTranslationGlossaryHash = translationGlossaryHash == null ? "" : translationGlossaryHash.trim();
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Transcript segments must not be empty.");
        }

        String sourceHash = sha256(canonicalSourceJson(segments));
        String cacheKey = sha256(String.join("\n",
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion,
                normalizedTargetLanguage,
                normalizedTranslationStyle,
                normalizedTranslationGlossaryHash,
                sourceHash
        ));
        return new TranslationCacheLookupBo(
                cacheKey,
                sourceHash,
                normalizedTargetLanguage,
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion,
                normalizedTranslationStyle,
                normalizedTranslationGlossaryHash
        );
    }

    private String canonicalSourceJson(List<TranscriptSegmentVo> segments) {
        List<Map<String, Object>> payload = segments.stream()
                .sorted(Comparator.comparingInt(TranscriptSegmentVo::index))
                .map(segment -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("index", segment.index());
                    item.put("startMs", segment.startMs());
                    item.put("endMs", segment.endMs());
                    item.put("text", requireText(segment.text(), "segment text"));
                    return item;
                })
                .toList();
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Translation cache source input could not be serialized.", ex);
        }
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Translation cache " + label + " must not be blank.");
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
