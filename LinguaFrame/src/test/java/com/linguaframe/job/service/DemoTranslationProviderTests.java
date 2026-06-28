package com.linguaframe.job.service;

import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.DemoTranslationProvider;
import com.linguaframe.job.service.impl.ModelCallSummaryServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoTranslationProviderTests {

    private final RecordingModelCallAuditService auditService = new RecordingModelCallAuditService();
    private final DemoTranslationProvider provider = new DemoTranslationProvider(
            auditService,
            new ModelCallSummaryServiceImpl()
    );

    @Test
    void demoProviderReturnsDeterministicChineseTextAndPreservesTiming() {
        var result = provider.translate("translation-job-1", "zh-CN", "FORMAL", List.of(
                new TranscriptSegmentVo(0, 0L, 1_800L, "Hello from LinguaFrame."),
                new TranscriptSegmentVo(1, 1_800L, 3_600L, "This demo transcript is deterministic.")
        ));

        assertThat(result.segments())
                .extracting(segment -> segment.index() + ":" + segment.startMs() + ":" + segment.endMs() + ":" + segment.text())
                .containsExactly(
                        "0:0:1800:LinguaFrame 向你问好。",
                        "1:1800:3600:这个演示字幕是确定性的。"
                );
        assertThat(auditService.successCommands).hasSize(1);
        var command = auditService.successCommands.getFirst();
        assertThat(command.jobId()).isEqualTo("translation-job-1");
        assertThat(command.stage()).isEqualTo(LocalizationJobStage.TARGET_SUBTITLE_EXPORT);
        assertThat(command.operation()).isEqualTo(ModelCallOperation.TRANSLATION);
        assertThat(command.provider()).isEqualTo(ModelCallProvider.DEMO);
        assertThat(command.model()).isEqualTo("demo-translation");
        assertThat(command.promptVersion()).isEqualTo("demo-translation-v1");
        assertThat(command.inputSummary()).isEqualTo("target=zh-CN, style=FORMAL, segments=2, sourceChars=61");
        assertThat(command.outputSummary()).isEqualTo("segments=2, targetChars=29");
        assertThat(command.inputSummary()).doesNotContain("Hello from LinguaFrame.");
        assertThat(command.outputSummary()).doesNotContain("LinguaFrame 向你问好。");
        assertThat(command.latencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void demoProviderPrefixesUnknownTextWithTargetLanguage() {
        var result = provider.translate("translation-job-2", "fr-FR", "CONCISE", List.of(
                new TranscriptSegmentVo(0, 0L, 1_000L, "Unknown source text.")
        ));

        assertThat(result.segments().getFirst().text()).isEqualTo("[fr-FR] Unknown source text.");
    }
}
