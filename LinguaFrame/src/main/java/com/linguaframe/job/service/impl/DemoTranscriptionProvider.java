package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.TranscriptionResultBo;
import com.linguaframe.job.domain.bo.TranscriptionSegmentBo;
import com.linguaframe.job.service.TranscriptionProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "linguaframe.transcription", name = "provider", havingValue = "demo", matchIfMissing = true)
public class DemoTranscriptionProvider implements TranscriptionProvider {

    @Override
    public TranscriptionResultBo transcribe(String jobId, byte[] audioContent) {
        return new TranscriptionResultBo(List.of(
                new TranscriptionSegmentBo(0, 0L, 1_800L, "Hello from LinguaFrame."),
                new TranscriptionSegmentBo(1, 1_800L, 3_600L, "This demo transcript is deterministic.")
        ));
    }
}
