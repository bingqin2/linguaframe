package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.impl.DemoTranscriptionProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTranscriptionProviderTests {

    private final RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
    private final DemoTranscriptionProvider provider = new DemoTranscriptionProvider(auditService);

    @Test
    void recordsSuccessfulDemoTranscriptionAudit() {
        var result = provider.transcribe("demo-transcription-job-1", new byte[] {1, 2, 3});

        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.startMs() + ":" + segment.endMs() + ":" + segment.text())
                .containsExactly(
                        "0:0:1800:Hello from LinguaFrame.",
                        "1:1800:3600:This demo transcript is deterministic."
                );
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("demo-transcription-job-1");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.TRANSCRIPTION);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.DEMO);
        assertThat(command.model()).isEqualTo("demo-transcription");
        assertThat(command.promptVersion()).isEqualTo("demo-transcription-v1");
        assertThat(command.audioSeconds()).isEqualByComparingTo("3.600");
        assertThat(command.latencyMs()).isGreaterThanOrEqualTo(0L);
    }
}
