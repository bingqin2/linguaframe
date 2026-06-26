package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.TranslationProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "linguaframe.translation", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTranslationProvider implements TranslationProvider {

    private static final Map<String, String> ZH_CN_TRANSLATIONS = Map.of(
            "Hello from LinguaFrame.", "LinguaFrame 向你问好。",
            "This demo transcript is deterministic.", "这个演示字幕是确定性的。"
    );

    private final ModelCallAuditService auditService;

    public DemoTranslationProvider(ModelCallAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public TranslationResultBo translate(String jobId, String targetLanguage, List<TranscriptSegmentVo> transcriptSegments) {
        long started = System.nanoTime();
        try {
            List<TranslationSegmentBo> segments = transcriptSegments.stream()
                    .map(segment -> new TranslationSegmentBo(
                            segment.index(),
                            segment.startMs(),
                            segment.endMs(),
                            translateText(targetLanguage, segment.text())
                    ))
                    .toList();
            TranslationResultBo result = new TranslationResultBo(segments);
            auditService.recordSuccess(command(jobId, elapsedMillis(started)));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(jobId, elapsedMillis(started)), ex.getMessage());
            throw ex;
        }
    }

    private CreateModelCallRecordCommand command(String jobId, long latencyMs) {
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSLATION,
                ModelCallProvider.DEMO,
                "demo-translation",
                "demo-translation-v1",
                latencyMs,
                null,
                null,
                null,
                null
        );
    }

    private String translateText(String targetLanguage, String sourceText) {
        if ("zh-CN".equals(targetLanguage) && ZH_CN_TRANSLATIONS.containsKey(sourceText)) {
            return ZH_CN_TRANSLATIONS.get(sourceText);
        }
        return "[" + targetLanguage + "] " + sourceText;
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
