package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TtsRequestBo;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.service.impl.DemoTtsProvider;
import com.linguaframe.job.service.impl.ModelCallSummaryServiceImpl;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTtsProviderTests {

    private final RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
    private final DemoTtsProvider provider = new DemoTtsProvider(
            auditService,
            new ModelCallSummaryServiceImpl()
    );

    @Test
    void recordsSuccessfulDemoTtsAudit() {
        var request = new TtsRequestBo("demo-tts-job-1", "zh-CN", "LinguaFrame 向你问好。");

        var result = provider.synthesize(request);

        assertThat(new String(result.audioContent(), StandardCharsets.UTF_8))
                .isEqualTo("LINGUAFRAME_DEMO_DUBBING_AUDIO\nzh-CN\nLinguaFrame 向你问好。");
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("demo-tts-job-1");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.DUBBING_AUDIO_GENERATION);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.TTS);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.DEMO);
        assertThat(command.model()).isEqualTo("demo-tts");
        assertThat(command.promptVersion()).isEqualTo("demo-tts-v1");
        assertThat(command.characterCount()).isEqualTo("LinguaFrame 向你问好。".length());
        assertThat(command.inputSummary()).isEqualTo("characters=17");
        assertThat(command.outputSummary()).isEqualTo("audioBytes=" + result.audioContent().length);
        assertThat(command.inputSummary()).doesNotContain("LinguaFrame 向你问好。");
        assertThat(command.latencyMs()).isGreaterThanOrEqualTo(0L);
    }
}
