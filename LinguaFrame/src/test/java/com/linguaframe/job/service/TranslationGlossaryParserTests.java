package com.linguaframe.job.service;

import com.linguaframe.job.service.impl.TranslationGlossaryParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationGlossaryParserTests {

    private final TranslationGlossaryParser parser = new TranslationGlossaryParser();

    @Test
    void acceptsArrowAndEqualsMappingsWithStableJsonAndHash() {
        var glossary = parser.parse("""
                Maya => 玛雅
                Tears of Steel = 钢铁之泪
                """);

        assertThat(glossary.entryCount()).isEqualTo(2);
        assertThat(glossary.entries())
                .extracting(entry -> entry.sourceTerm() + "=>" + entry.targetTerm())
                .containsExactly("Maya=>玛雅", "Tears of Steel=>钢铁之泪");
        assertThat(glossary.json()).isEqualTo("""
                [{"sourceTerm":"Maya","targetTerm":"玛雅"},{"sourceTerm":"Tears of Steel","targetTerm":"钢铁之泪"}]""");
        assertThat(glossary.hash()).matches("[a-f0-9]{64}");
    }

    @Test
    void normalizesBlankInputToEmptyGlossary() {
        var glossary = parser.parse("   \n\t");

        assertThat(glossary.entryCount()).isZero();
        assertThat(glossary.entries()).isEmpty();
        assertThat(glossary.json()).isEqualTo("[]");
        assertThat(glossary.hash()).isEmpty();
    }

    @Test
    void rejectsMalformedEntries() {
        assertThatThrownBy(() -> parser.parse("Maya 玛雅"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must use '=>' or '='");
        assertThatThrownBy(() -> parser.parse("=> 玛雅"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source term");
        assertThatThrownBy(() -> parser.parse("Maya => "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("target term");
    }

    @Test
    void rejectsTooManyEntriesAndTooLongValues() {
        StringBuilder tooManyEntries = new StringBuilder();
        for (int index = 0; index < 21; index++) {
            tooManyEntries.append("source").append(index).append(" => target").append(index).append('\n');
        }
        assertThatThrownBy(() -> parser.parse(tooManyEntries.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 20 entries");

        assertThatThrownBy(() -> parser.parse("source => " + "x".repeat(81)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("80 characters");
        assertThatThrownBy(() -> parser.parse("x".repeat(2001)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2000 characters");
    }

    @Test
    void readsStoredJsonBackToEntries() {
        var glossary = parser.fromStoredJson("""
                [{"sourceTerm":"Maya","targetTerm":"玛雅"}]""", "abc123", 1);

        assertThat(glossary.entryCount()).isEqualTo(1);
        assertThat(glossary.hash()).isEqualTo("abc123");
        assertThat(glossary.entries().getFirst().sourceTerm()).isEqualTo("Maya");
    }
}
