package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.SubtitlePolishingResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.ModelCallSummaryService;
import com.linguaframe.job.service.SubtitlePolishingProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "linguaframe.translation", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoSubtitlePolishingProvider implements SubtitlePolishingProvider {

    private final ModelCallAuditService auditService;
    private final ModelCallSummaryService summaryService;

    public DemoSubtitlePolishingProvider(ModelCallAuditService auditService, ModelCallSummaryService summaryService) {
        this.auditService = auditService;
        this.summaryService = summaryService;
    }

    @Override
    public SubtitlePolishingResultBo polish(
            String jobId,
            String targetLanguage,
            String subtitlePolishingMode,
            List<SubtitleSegmentVo> subtitles
    ) {
        long started = System.nanoTime();
        try {
            List<TranslationSegmentBo> segments = subtitles.stream()
                    .map(segment -> new TranslationSegmentBo(
                            segment.index(),
                            segment.startMs(),
                            segment.endMs(),
                            polishText(subtitlePolishingMode, segment.text())
                    ))
                    .toList();
            SubtitlePolishingResultBo result = new SubtitlePolishingResultBo(segments);
            auditService.recordSuccess(command(
                    jobId,
                    elapsedMillis(started),
                    inputSummary(targetLanguage, subtitlePolishingMode, subtitles),
                    outputSummary(result)
            ));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(
                    jobId,
                    elapsedMillis(started),
                    inputSummary(targetLanguage, subtitlePolishingMode, subtitles),
                    null
            ), ex.getMessage());
            throw ex;
        }
    }

    private String polishText(String mode, String text) {
        String normalized = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if ("STRICT".equalsIgnoreCase(mode)) {
            return normalized;
        }
        return normalized
                .replace("直译 字幕", "自然字幕")
                .replace("确定性的", "稳定可复现的");
    }

    private CreateModelCallRecordCommand command(
            String jobId,
            long latencyMs,
            String inputSummary,
            String outputSummary
    ) {
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.SUBTITLE_POLISHING,
                ModelCallOperation.SUBTITLE_POLISHING,
                ModelCallProvider.DEMO,
                "demo-subtitle-polishing",
                "demo-subtitle-polishing-v1",
                latencyMs,
                null,
                null,
                null,
                null,
                inputSummary,
                outputSummary
        );
    }

    private String inputSummary(String targetLanguage, String mode, List<SubtitleSegmentVo> subtitles) {
        return summaryService.translationInput(
                targetLanguage,
                mode,
                subtitles.size(),
                subtitles.stream().map(SubtitleSegmentVo::text).mapToInt(this::length).sum(),
                0
        );
    }

    private String outputSummary(SubtitlePolishingResultBo result) {
        return summaryService.translationOutput(
                result.segments().size(),
                result.segments().stream().map(TranslationSegmentBo::text).mapToInt(this::length).sum()
        );
    }

    private int length(String value) {
        return value == null ? 0 : value.length();
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
