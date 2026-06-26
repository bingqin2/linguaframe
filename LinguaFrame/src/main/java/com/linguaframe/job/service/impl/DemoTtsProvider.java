package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.CreateModelCallRecordCommand;
import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.bo.TtsResultBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.ModelCallAuditService;
import com.linguaframe.job.service.TtsProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@ConditionalOnProperty(prefix = "linguaframe.tts", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTtsProvider implements TtsProvider {

    private final ModelCallAuditService auditService;

    public DemoTtsProvider(ModelCallAuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public TtsResultBo synthesize(TtsRequestBo request) {
        long started = System.nanoTime();
        try {
            byte[] audio = ("LINGUAFRAME_DEMO_DUBBING_AUDIO\n" + request.language() + "\n" + request.text())
                    .getBytes(StandardCharsets.UTF_8);
            TtsResultBo result = new TtsResultBo(audio, "dubbing-audio.mp3", "audio/mpeg");
            auditService.recordSuccess(command(request, elapsedMillis(started)));
            return result;
        } catch (RuntimeException ex) {
            auditService.recordFailure(command(request, elapsedMillis(started)), ex.getMessage());
            throw ex;
        }
    }

    private CreateModelCallRecordCommand command(TtsRequestBo request, long latencyMs) {
        return new CreateModelCallRecordCommand(
                request.jobId(),
                LocalizationJobStage.DUBBING_AUDIO_GENERATION,
                ModelCallOperation.TTS,
                ModelCallProvider.DEMO,
                "demo-tts",
                "demo-tts-v1",
                latencyMs,
                null,
                null,
                null,
                characterCount(request)
        );
    }

    private Integer characterCount(TtsRequestBo request) {
        return request.text() == null ? 0 : request.text().length();
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }
}
