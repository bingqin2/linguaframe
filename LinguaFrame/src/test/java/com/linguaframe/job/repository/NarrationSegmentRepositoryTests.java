package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NarrationSegmentRepositoryTests {

    @Autowired
    private NarrationSegmentRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM narration_segments").update();
    }

    @Test
    void replacesAndReadsSegmentsByJobId() {
        repository.replaceSegments("job-narration", List.of(
                new NarrationSegmentRecord(
                        "narration-1",
                        "job-narration",
                        0,
                        new BigDecimal("15.000"),
                        new BigDecimal("28.000"),
                        "Explain first scene.",
                        "alloy",
                        new BigDecimal("0.250"),
                        new BigDecimal("1.500"),
                        125,
                        Instant.parse("2026-06-29T10:00:00Z"),
                        Instant.parse("2026-06-29T10:01:00Z")
                ),
                new NarrationSegmentRecord(
                        "narration-2",
                        "job-narration",
                        1,
                        new BigDecimal("55.000"),
                        new BigDecimal("70.500"),
                        "Explain second scene.",
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-06-29T10:00:00Z"),
                        Instant.parse("2026-06-29T10:01:00Z")
                )
        ));

        List<NarrationSegmentRecord> records = repository.findByJobId("job-narration");

        assertThat(records).hasSize(2);
        assertThat(records)
                .extracting(record -> record.segmentIndex()
                        + ":" + record.startSeconds()
                        + ":" + record.endSeconds()
                        + ":" + record.text()
                        + ":" + record.voice()
                        + ":" + record.duckingVolume()
                        + ":" + record.narrationVolume()
                        + ":" + record.fadeDurationMs())
                .containsExactly(
                        "0:15.000:28.000:Explain first scene.:alloy:0.250:1.500:125",
                        "1:55.000:70.500:Explain second scene.:null:null:null:null"
                );
    }

    @Test
    void replaceDeletesOldRowsForSameJobOnly() {
        repository.replaceSegments("job-narration", List.of(new NarrationSegmentRecord(
                "narration-old",
                "job-narration",
                0,
                new BigDecimal("1.000"),
                new BigDecimal("2.000"),
                "Old",
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-29T10:00:00Z"),
                Instant.parse("2026-06-29T10:01:00Z")
        )));
        repository.replaceSegments("other-job", List.of(new NarrationSegmentRecord(
                "narration-other",
                "other-job",
                0,
                new BigDecimal("3.000"),
                new BigDecimal("4.000"),
                "Other",
                null,
                null,
                null,
                null,
                Instant.parse("2026-06-29T10:00:00Z"),
                Instant.parse("2026-06-29T10:01:00Z")
        )));

        repository.replaceSegments("job-narration", List.of(new NarrationSegmentRecord(
                "narration-new",
                "job-narration",
                0,
                new BigDecimal("5.000"),
                new BigDecimal("6.000"),
                "New",
                "verse",
                new BigDecimal("0.400"),
                null,
                500,
                Instant.parse("2026-06-29T11:00:00Z"),
                Instant.parse("2026-06-29T11:01:00Z")
        )));

        assertThat(repository.findByJobId("job-narration"))
                .extracting(NarrationSegmentRecord::text)
                .containsExactly("New");
        assertThat(repository.findByJobId("other-job"))
                .extracting(NarrationSegmentRecord::text)
                .containsExactly("Other");
    }
}
