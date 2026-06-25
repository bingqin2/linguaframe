package com.linguaframe.media.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UploadIntakeSchemaTests {

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void flywayCreatesUploadIntakeTables() {
        Integer videoTableCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_name = 'videos'
                        """)
                .query(Integer.class)
                .single();
        Integer jobTableCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_name = 'localization_jobs'
                        """)
                .query(Integer.class)
                .single();
        Integer dispatchEventTableCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_name = 'job_dispatch_events'
                        """)
                .query(Integer.class)
                .single();
        Integer timelineEventTableCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_name = 'job_timeline_events'
                        """)
                .query(Integer.class)
                .single();
        Integer executionColumnCount = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_name = 'localization_jobs'
                          AND column_name IN (
                              'started_at',
                              'completed_at',
                              'failed_at',
                              'failure_stage',
                              'failure_reason',
                              'retry_count',
                              'updated_at'
                          )
                        """)
                .query(Integer.class)
                .single();

        assertThat(videoTableCount).isEqualTo(1);
        assertThat(jobTableCount).isEqualTo(1);
        assertThat(dispatchEventTableCount).isEqualTo(1);
        assertThat(timelineEventTableCount).isEqualTo(1);
        assertThat(executionColumnCount).isEqualTo(7);
    }
}
