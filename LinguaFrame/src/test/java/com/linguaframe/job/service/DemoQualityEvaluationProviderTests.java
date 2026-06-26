package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.QualityEvaluationRequestBo;
import com.linguaframe.job.domain.vo.SubtitleSegmentVo;
import com.linguaframe.job.domain.vo.TranscriptSegmentVo;
import com.linguaframe.job.service.impl.DemoQualityEvaluationProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoQualityEvaluationProviderTests {

    private final DemoQualityEvaluationProvider provider = new DemoQualityEvaluationProvider();

    @Test
    void returnsDeterministicQualityResult() {
        var result = provider.evaluate(new QualityEvaluationRequestBo(
                "quality-job-demo",
                "zh-CN",
                List.of(new TranscriptSegmentVo(0, 0L, 1_000L, "Hello from LinguaFrame.")),
                List.of(new SubtitleSegmentVo("zh-CN", 0, 0L, 1_000L, "LinguaFrame 向你问好。"))
        ));

        assertThat(result.score()).isEqualTo(92);
        assertThat(result.verdict()).isEqualTo("GOOD");
        assertThat(result.completeness()).isEqualTo(95);
        assertThat(result.readability()).isEqualTo(92);
        assertThat(result.timingPreservation()).isEqualTo(94);
        assertThat(result.naturalness()).isEqualTo(88);
        assertThat(result.issues()).containsExactly("Demo evaluation found no blocking subtitle quality issues.");
        assertThat(result.suggestedFixes()).containsExactly("Review tone and terminology before publishing.");
    }
}
