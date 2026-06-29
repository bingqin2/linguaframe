package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcNarrationMixSettingsRepository implements NarrationMixSettingsRepository {

    private final JdbcClient jdbcClient;

    public JdbcNarrationMixSettingsRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<NarrationMixSettingsRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            job_id,
                            ducking_volume,
                            narration_volume,
                            fade_duration_ms,
                            updated_at
                        FROM narration_mix_settings
                        WHERE job_id = :jobId
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public NarrationMixSettingsRecord upsert(NarrationMixSettingsRecord settings) {
        jdbcClient.sql("""
                        MERGE INTO narration_mix_settings (
                            job_id,
                            ducking_volume,
                            narration_volume,
                            fade_duration_ms,
                            updated_at
                        )
                        KEY (job_id)
                        VALUES (
                            :jobId,
                            :duckingVolume,
                            :narrationVolume,
                            :fadeDurationMs,
                            :updatedAt
                        )
                        """)
                .param("jobId", settings.jobId())
                .param("duckingVolume", settings.duckingVolume())
                .param("narrationVolume", settings.narrationVolume())
                .param("fadeDurationMs", settings.fadeDurationMs())
                .param("updatedAt", Timestamp.from(settings.updatedAt()))
                .update();
        return settings;
    }

    @Override
    public void deleteByJobId(String jobId) {
        jdbcClient.sql("DELETE FROM narration_mix_settings WHERE job_id = :jobId")
                .param("jobId", jobId)
                .update();
    }

    private NarrationMixSettingsRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NarrationMixSettingsRecord(
                rs.getString("job_id"),
                rs.getBigDecimal("ducking_volume"),
                rs.getBigDecimal("narration_volume"),
                rs.getInt("fade_duration_ms"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
