package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.TranscriptSegmentRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class TranscriptSegmentRepository {

    private final JdbcClient jdbcClient;

    public TranscriptSegmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void saveAll(List<TranscriptSegmentRecord> records) {
        for (TranscriptSegmentRecord record : records) {
            save(record);
        }
    }

    public List<TranscriptSegmentRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            segment_index,
                            start_ms,
                            end_ms,
                            text,
                            created_at
                        FROM transcript_segments
                        WHERE job_id = :jobId
                        ORDER BY segment_index
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    public void deleteByJobId(String jobId) {
        jdbcClient.sql("DELETE FROM transcript_segments WHERE job_id = :jobId")
                .param("jobId", jobId)
                .update();
    }

    private void save(TranscriptSegmentRecord record) {
        jdbcClient.sql("""
                        INSERT INTO transcript_segments (
                            id,
                            job_id,
                            segment_index,
                            start_ms,
                            end_ms,
                            text,
                            created_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :segmentIndex,
                            :startMs,
                            :endMs,
                            :text,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("segmentIndex", record.segmentIndex())
                .param("startMs", record.startMs())
                .param("endMs", record.endMs())
                .param("text", record.text())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    private TranscriptSegmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TranscriptSegmentRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getInt("segment_index"),
                rs.getLong("start_ms"),
                rs.getLong("end_ms"),
                rs.getString("text"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
