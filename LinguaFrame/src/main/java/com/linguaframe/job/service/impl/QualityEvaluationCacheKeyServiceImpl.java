package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.QualityEvaluationCacheLookupBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.QualityEvaluationCacheKeyService;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Service
public class QualityEvaluationCacheKeyServiceImpl implements QualityEvaluationCacheKeyService {

    @Override
    public QualityEvaluationCacheLookupBo build(
            String language,
            String provider,
            String model,
            String promptVersion,
            List<TranscriptSegmentVo> sourceSegments,
            List<SubtitleSegmentVo> targetSegments
    ) {
        String normalizedLanguage = requireText(language, "language");
        String normalizedProvider = requireText(provider, "provider");
        String normalizedModel = requireText(model, "model");
        String normalizedPromptVersion = requireText(promptVersion, "prompt version");
        requireSegments(sourceSegments, "source segments");
        requireSegments(targetSegments, "target segments");

        String sourceHash = sha256(sourcePayload(sourceSegments));
        String targetHash = sha256(targetPayload(targetSegments));
        String cacheKey = sha256(String.join("\n",
                normalizedLanguage,
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion,
                sourceHash,
                targetHash
        ).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new QualityEvaluationCacheLookupBo(
                cacheKey,
                sourceHash,
                targetHash,
                normalizedLanguage,
                normalizedProvider,
                normalizedModel,
                normalizedPromptVersion
        );
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Quality evaluation cache " + label + " must not be blank.");
        }
        return value.trim();
    }

    private void requireSegments(List<?> segments, String label) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Quality evaluation cache " + label + " must not be empty.");
        }
    }

    private byte[] sourcePayload(List<TranscriptSegmentVo> segments) {
        StringBuilder builder = new StringBuilder();
        segments.stream()
                .sorted(java.util.Comparator.comparingInt(TranscriptSegmentVo::index))
                .forEach(segment -> builder
                        .append(segment.index()).append('\t')
                        .append(segment.startMs()).append('\t')
                        .append(segment.endMs()).append('\t')
                        .append(segment.text() == null ? "" : segment.text())
                        .append('\n'));
        return builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] targetPayload(List<SubtitleSegmentVo> segments) {
        StringBuilder builder = new StringBuilder();
        segments.stream()
                .sorted(java.util.Comparator.comparingInt(SubtitleSegmentVo::index))
                .forEach(segment -> builder
                        .append(segment.language()).append('\t')
                        .append(segment.index()).append('\t')
                        .append(segment.startMs()).append('\t')
                        .append(segment.endMs()).append('\t')
                        .append(segment.text() == null ? "" : segment.text())
                        .append('\n'));
        return builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
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
