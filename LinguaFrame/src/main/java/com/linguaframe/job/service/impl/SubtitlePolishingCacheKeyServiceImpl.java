package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.SubtitlePolishingCacheLookupBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.SubtitlePolishingCacheKeyService;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SubtitlePolishingCacheKeyServiceImpl implements SubtitlePolishingCacheKeyService {

    private final ObjectMapper objectMapper;

    public SubtitlePolishingCacheKeyServiceImpl() {
        this(new ObjectMapper());
    }

    public SubtitlePolishingCacheKeyServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public SubtitlePolishingCacheLookupBo build(
            String targetLanguage,
            String provider,
            String model,
            String promptVersion,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    ) {
        String normalizedTargetLanguage = requireText(targetLanguage, "target language");
        String normalizedProvider = requireText(provider, "provider");
        String normalizedModel = requireText(model, "model");
        String normalizedPromptVersion = requireText(promptVersion, "prompt version");
        String normalizedMode = requireText(subtitlePolishingMode, "subtitle polishing mode").toUpperCase();
        if (subtitles == null || subtitles.isEmpty()) {
            throw new IllegalArgumentException("Subtitle polishing cache segments must not be empty.");
        }

        String sourceHash = sha256(canonicalSourceJson(subtitles));
        String cacheKey = sha256(String.join("\n",
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion,
                normalizedTargetLanguage,
                normalizedMode,
                sourceHash
        ));
        return new SubtitlePolishingCacheLookupBo(
                cacheKey,
                sourceHash,
                normalizedTargetLanguage,
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion,
                normalizedMode
        );
    }

    private String canonicalSourceJson(List<SubtitleSegmentVo> subtitles) {
        List<Map<String, Object>> payload = subtitles.stream()
                .sorted(Comparator.comparingInt(SubtitleSegmentVo::index))
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
            throw new IllegalStateException("Subtitle polishing cache source input could not be serialized.", ex);
        }
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Subtitle polishing cache " + label + " must not be blank.");
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
