package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.TranslationResultBo;
import com.linguaframe.job.domain.bo.TranslationSegmentBo;
import com.linguaframe.job.domain.entity.SubtitleSegmentRecord;
import com.linguaframe.job.repository.SubtitleSegmentRepository;
import com.linguaframe.job.service.impl.SubtitleServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubtitleServiceTests {

    private final FakeSubtitleSegmentRepository repository = new FakeSubtitleSegmentRepository();
    private final SubtitleService service = new SubtitleServiceImpl(
            repository,
            Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void replacesOnlyTargetLanguageAndReturnsOrderedSubtitlesWithTiming() {
        repository.saveAll(List.of(
                new SubtitleSegmentRecord(
                        "old-zh",
                        "subtitle-service-job",
                        "zh-CN",
                        0,
                        0L,
                        1_000L,
                        "旧字幕",
                        Instant.parse("2026-06-26T09:00:00Z")
                ),
                new SubtitleSegmentRecord(
                        "old-en",
                        "subtitle-service-job",
                        "en-US",
                        0,
                        0L,
                        1_000L,
                        "Old subtitle",
                        Instant.parse("2026-06-26T09:00:00Z")
                )
        ));

        var subtitles = service.replaceSubtitles("subtitle-service-job", "zh-CN", new TranslationResultBo(List.of(
                new TranslationSegmentBo(1, 1_800L, 3_600L, "  这个演示字幕是确定性的。 "),
                new TranslationSegmentBo(0, 0L, 1_800L, "LinguaFrame 向你问好。")
        )));

        assertThat(subtitles)
                .extracting(subtitle -> subtitle.language() + ":" + subtitle.index() + ":" + subtitle.startMs() + ":" + subtitle.endMs() + ":" + subtitle.text())
                .containsExactly(
                        "zh-CN:0:0:1800:LinguaFrame 向你问好。",
                        "zh-CN:1:1800:3600:这个演示字幕是确定性的。"
                );
        assertThat(service.listSubtitles("subtitle-service-job", "en-US"))
                .extracting(subtitle -> subtitle.text())
                .containsExactly("Old subtitle");
    }

    @Test
    void rejectsBlankLanguageBlankTextAndInvalidTiming() {
        assertThatThrownBy(() -> service.replaceSubtitles(
                "subtitle-service-job",
                " ",
                new TranslationResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, "text")))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("language");

        assertThatThrownBy(() -> service.replaceSubtitles(
                "subtitle-service-job",
                "zh-CN",
                new TranslationResultBo(List.of(new TranslationSegmentBo(0, 0L, 1_000L, " ")))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");

        assertThatThrownBy(() -> service.replaceSubtitles(
                "subtitle-service-job",
                "zh-CN",
                new TranslationResultBo(List.of(new TranslationSegmentBo(0, 1_000L, 1_000L, "text")))
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endMs");
    }

    private static class FakeSubtitleSegmentRepository extends SubtitleSegmentRepository {

        private final List<SubtitleSegmentRecord> records = new ArrayList<>();

        private FakeSubtitleSegmentRepository() {
            super(null);
        }

        @Override
        public void saveAll(List<SubtitleSegmentRecord> records) {
            this.records.addAll(records);
        }

        @Override
        public List<SubtitleSegmentRecord> findByJobIdAndLanguage(String jobId, String language) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .filter(record -> record.language().equals(language))
                    .sorted(Comparator.comparingInt(SubtitleSegmentRecord::segmentIndex))
                    .toList();
        }

        @Override
        public void deleteByJobIdAndLanguage(String jobId, String language) {
            records.removeIf(record -> record.jobId().equals(jobId) && record.language().equals(language));
        }
    }
}
