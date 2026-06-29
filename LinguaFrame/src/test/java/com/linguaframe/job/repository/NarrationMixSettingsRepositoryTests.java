package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NarrationMixSettingsRepositoryTests {

    @Autowired
    private NarrationMixSettingsRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM narration_mix_settings").update();
    }

    @Test
    void insertsAndReadsSettingsByJobId() {
        repository.upsert(settings("job-mix", "0.250", "1.500", 300));

        assertThat(repository.findByJobId("job-mix"))
                .hasValueSatisfying(record -> {
                    assertThat(record.jobId()).isEqualTo("job-mix");
                    assertThat(record.duckingVolume()).isEqualByComparingTo("0.250");
                    assertThat(record.narrationVolume()).isEqualByComparingTo("1.500");
                    assertThat(record.fadeDurationMs()).isEqualTo(300);
                    assertThat(record.updatedAt()).isEqualTo(Instant.parse("2026-06-29T10:00:00Z"));
                });
    }

    @Test
    void upsertReplacesExistingSettingsForSameJobOnly() {
        repository.upsert(settings("job-mix", "0.350", "1.000", 250));
        repository.upsert(settings("other-job", "0.200", "1.200", 100));

        repository.upsert(settings("job-mix", "0.100", "1.800", 500));

        assertThat(repository.findByJobId("job-mix"))
                .hasValueSatisfying(record -> {
                    assertThat(record.duckingVolume()).isEqualByComparingTo("0.100");
                    assertThat(record.narrationVolume()).isEqualByComparingTo("1.800");
                    assertThat(record.fadeDurationMs()).isEqualTo(500);
                });
        assertThat(repository.findByJobId("other-job"))
                .hasValueSatisfying(record -> {
                    assertThat(record.duckingVolume()).isEqualByComparingTo("0.200");
                    assertThat(record.narrationVolume()).isEqualByComparingTo("1.200");
                    assertThat(record.fadeDurationMs()).isEqualTo(100);
                });
    }

    @Test
    void returnsEmptyWhenSettingsDoNotExist() {
        assertThat(repository.findByJobId("missing-job")).isEmpty();
    }

    @Test
    void deletesSettingsForJobOnly() {
        repository.upsert(settings("job-mix", "0.350", "1.000", 250));
        repository.upsert(settings("other-job", "0.200", "1.200", 100));

        repository.deleteByJobId("job-mix");

        assertThat(repository.findByJobId("job-mix")).isEmpty();
        assertThat(repository.findByJobId("other-job")).isPresent();
    }

    private NarrationMixSettingsRecord settings(
            String jobId,
            String duckingVolume,
            String narrationVolume,
            int fadeDurationMs
    ) {
        return new NarrationMixSettingsRecord(
                jobId,
                new BigDecimal(duckingVolume),
                new BigDecimal(narrationVolume),
                fadeDurationMs,
                Instant.parse("2026-06-29T10:00:00Z")
        );
    }
}
