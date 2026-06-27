package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.ModelCallSummaryService;
import com.linguaframe.job.service.TranscriptionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "linguaframe.transcription", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTranscriptionProvider implements TranscriptionProvider {

    private static final BigDecimal ONE_THOUSAND = BigDecimal.valueOf(1_000L);

    private final ModelCallAuditService auditService;
    private final ModelCallSummaryService summaryService;

    public DemoTranscriptionProvider(ModelCallAuditService auditService, ModelCallSummaryService summaryService) {
        this.auditService = auditService;
        this.summaryService = summaryService;
    }

    @Override
    public TranscriptionResultBo transcribe(String jobId, byte[] audioContent) {
        long started = System.nanoTime();
        try {
            TranscriptionResultBo result = new TranscriptionResultBo(List.of(
                    new TranscriptionSegmentBo(0, 0L, 1_800L, "Hello from LinguaFrame."),
                    new TranscriptionSegmentBo(1, 1_800L, 3_600L, "This demo transcript is deterministic.")
            ));
            auditService.recordSuccess(command(jobId, elapsedMillis(started), result));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(jobId, elapsedMillis(started), null), ex.getMessage());
            throw ex;
        }
    }

    private CreateModelCallRecordCommand command(String jobId, long latencyMs, TranscriptionResultBo result) {
        BigDecimal audioSeconds = result == null ? null : audioSeconds(result);
        return new CreateModelCallRecordCommand(
                jobId,
                LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                ModelCallOperation.TRANSCRIPTION,
                ModelCallProvider.DEMO,
                "demo-transcription",
                "demo-transcription-v1",
                latencyMs,
                null,
                null,
                audioSeconds,
                null,
                audioSeconds == null ? null : summaryService.transcriptionInput(audioSeconds),
                result == null ? null : summaryService.transcriptionOutput(result.segments().size(), transcriptCharacterCount(result))
        );
    }

    private int transcriptCharacterCount(TranscriptionResultBo result) {
        return result.segments().stream()
                .map(TranscriptionSegmentBo::text)
                .mapToInt(value -> value == null ? 0 : value.length())
                .sum();
    }

    private BigDecimal audioSeconds(TranscriptionResultBo result) {
        return result.segments().stream()
                .map(TranscriptionSegmentBo::endMs)
                .max(Long::compareTo)
                .map(endMs -> BigDecimal.valueOf(endMs).divide(ONE_THOUSAND, 3, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
