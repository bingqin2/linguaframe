package com.linguaframe.job.service;

import com.linguaframe.job.service.impl.NarrationQuickScriptParser;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationQuickScriptParserTests {

    private final NarrationQuickScriptParser parser = new NarrationQuickScriptParser();

    @Test
    void parsesTimestampFormatsVoicesAndInheritedVoiceRows() {
        NarrationQuickScriptParser.Result result = parser.parse("""
                15-28 | alloy | Explain the opening gesture.
                00:55-01:10 || Inherit the default voice.
                01:02:03-01:02:08 | verse | Explain the long-form timestamp.
                """);

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(result.segmentCount()).isEqualTo(3);
        assertThat(result.characterCount()).isEqualTo(86);
        assertThat(result.voiceSummary()).isEqualTo("alloy: 1, inherited: 1, verse: 1");
        assertThat(result.toSaveRequest().segments()).hasSize(3);
        assertThat(result.toSaveRequest().segments().get(0).index()).isEqualTo(0);
        assertThat(result.toSaveRequest().segments().get(0).startSeconds()).isEqualByComparingTo(new BigDecimal("15.000"));
        assertThat(result.toSaveRequest().segments().get(0).endSeconds()).isEqualByComparingTo(new BigDecimal("28.000"));
        assertThat(result.toSaveRequest().segments().get(0).voice()).isEqualTo("alloy");
        assertThat(result.toSaveRequest().segments().get(1).voice()).isNull();
        assertThat(result.toSaveRequest().segments().get(2).startSeconds()).isEqualByComparingTo(new BigDecimal("3723.000"));
        assertThat(result.toSaveRequest().segments().get(2).endSeconds()).isEqualByComparingTo(new BigDecimal("3728.000"));
    }

    @Test
    void returnsEmptyValidResultForBlankScript() {
        NarrationQuickScriptParser.Result result = parser.parse(" \n \t ");

        assertThat(result.valid()).isTrue();
        assertThat(result.segmentCount()).isZero();
        assertThat(result.characterCount()).isZero();
        assertThat(result.voiceSummary()).isEqualTo("none");
        assertThat(result.toSaveRequest().segments()).isEmpty();
    }

    @Test
    void reportsMalformedRowsAndDoesNotProduceSegments() {
        NarrationQuickScriptParser.Result result = parser.parse("""
                00:10-00:05 | alloy | Backwards.
                not a row
                00:20-00:30 | alloy |
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.toSaveRequest().segments()).isEmpty();
        assertThat(result.errors()).containsExactly(
                "Row 1: end time must be greater than start time.",
                "Row 2: expected START-END | VOICE | TEXT.",
                "Row 3: narration text must not be blank."
        );
    }

    @Test
    void reportsOverlappingRows() {
        NarrationQuickScriptParser.Result result = parser.parse("""
                00:10-00:20 | alloy | First.
                00:19-00:30 | verse | Overlap.
                """);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("Rows 1 and 2: narration segments must not overlap.");
        assertThat(result.toSaveRequest().segments()).isEmpty();
    }

    @Test
    void reportsTooManyRowsAndTooLongText() {
        StringBuilder script = new StringBuilder();
        for (int index = 0; index < 21; index += 1) {
            script.append(index * 2).append('-').append(index * 2 + 1).append(" | alloy | Row ").append(index).append('\n');
        }
        script.append("100-101 | alloy | ").append("x".repeat(1001));

        NarrationQuickScriptParser.Result result = parser.parse(script.toString());

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "Narration quick script supports at most 20 rows.",
                "Row 22: narration text must be at most 1000 characters."
        );
        assertThat(result.toSaveRequest().segments()).isEmpty();
    }
}
