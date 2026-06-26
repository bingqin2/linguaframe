package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.SubtitleSegmentRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class SubtitleSegmentRepository {

    private final JdbcClient jdbcClient;

    public SubtitleSegmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void saveAll(List<SubtitleSegmentRecord> records) {
        for (SubtitleSegmentRecord record : records) {
            save(record);
        }
    }

    public List<SubtitleSegmentRecord> findByJobIdAndLanguage(String jobId, String language) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            language,
                            segment_index,
                            start_ms,
                            end_ms,
                            text,
                            created_at
                        FROM subtitle_segments
                        WHERE job_id = :jobId AND language = :language
                        ORDER BY segment_index
                        """)
                .param("jobId", jobId)
                .param("language", language)
                .query(this::mapRow)
                .list();
    }

    public void deleteByJobIdAndLanguage(String jobId, String language) {
        jdbcClient.sql("DELETE FROM subtitle_segments WHERE job_id = :jobId AND language = :language")
                .param("jobId", jobId)
                .param("language", language)
                .update();
    }

    private void save(SubtitleSegmentRecord record) {
        jdbcClient.sql("""
                        INSERT INTO subtitle_segments (
                            id,
                            job_id,
                            language,
                            segment_index,
                            start_ms,
                            end_ms,
                            text,
                            created_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :language,
                            :segmentIndex,
                            :startMs,
                            :endMs,
                            :text,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("language", record.language())
                .param("segmentIndex", record.segmentIndex())
                .param("startMs", record.startMs())
                .param("endMs", record.endMs())
                .param("text", record.text())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    private SubtitleSegmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SubtitleSegmentRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getString("language"),
                rs.getInt("segment_index"),
                rs.getLong("start_ms"),
                rs.getLong("end_ms"),
                rs.getString("text"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
