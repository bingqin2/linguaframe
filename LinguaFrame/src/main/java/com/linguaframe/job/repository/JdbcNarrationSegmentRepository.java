package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class JdbcNarrationSegmentRepository implements NarrationSegmentRepository {

    private final JdbcClient jdbcClient;

    public JdbcNarrationSegmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
        deleteByJobId(jobId);
        for (NarrationSegmentRecord segment : segments) {
            insert(segment);
        }
    }

    @Override
    public List<NarrationSegmentRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            segment_index,
                            start_seconds,
                            end_seconds,
                            text,
                            voice,
                            ducking_volume,
                            narration_volume,
                            fade_duration_ms,
                            created_at,
                            updated_at
                        FROM narration_segments
                        WHERE job_id = :jobId
                        ORDER BY segment_index
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    @Override
    public void deleteByJobId(String jobId) {
        jdbcClient.sql("DELETE FROM narration_segments WHERE job_id = :jobId")
                .param("jobId", jobId)
                .update();
    }

    private void insert(NarrationSegmentRecord record) {
        jdbcClient.sql("""
                        INSERT INTO narration_segments (
                            id,
                            job_id,
                            segment_index,
                            start_seconds,
                            end_seconds,
                            text,
                            voice,
                            ducking_volume,
                            narration_volume,
                            fade_duration_ms,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :segmentIndex,
                            :startSeconds,
                            :endSeconds,
                            :text,
                            :voice,
                            :duckingVolume,
                            :narrationVolume,
                            :fadeDurationMs,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("segmentIndex", record.segmentIndex())
                .param("startSeconds", record.startSeconds())
                .param("endSeconds", record.endSeconds())
                .param("text", record.text())
                .param("voice", record.voice())
                .param("duckingVolume", record.duckingVolume())
                .param("narrationVolume", record.narrationVolume())
                .param("fadeDurationMs", record.fadeDurationMs())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    private NarrationSegmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NarrationSegmentRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getInt("segment_index"),
                rs.getBigDecimal("start_seconds"),
                rs.getBigDecimal("end_seconds"),
                rs.getString("text"),
                rs.getString("voice"),
                rs.getBigDecimal("ducking_volume"),
                rs.getBigDecimal("narration_volume"),
                nullableInteger(rs, "fade_duration_ms"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
