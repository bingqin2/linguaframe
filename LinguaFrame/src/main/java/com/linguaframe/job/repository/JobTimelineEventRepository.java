package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.JobTimelineEventRecord;
import com.linguaframe.job.domain.enums.JobTimelineEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JobTimelineEventRepository {

    private static final int MAX_MESSAGE_LENGTH = 512;
    private static final int MAX_ERROR_SUMMARY_LENGTH = 512;

    private final JdbcClient jdbcClient;

    public JobTimelineEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(JobTimelineEventRecord record) {
        jdbcClient.sql("""
                        INSERT INTO job_timeline_events (
                            id,
                            job_id,
                            stage,
                            status,
                            message,
                            duration_ms,
                            error_summary,
                            occurred_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :stage,
                            :status,
                            :message,
                            :durationMs,
                            :errorSummary,
                            :occurredAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("stage", record.stage().name())
                .param("status", record.status().name())
                .param("message", truncate(record.message(), MAX_MESSAGE_LENGTH))
                .param("durationMs", record.durationMs())
                .param("errorSummary", truncate(record.errorSummary(), MAX_ERROR_SUMMARY_LENGTH))
                .param("occurredAt", Timestamp.from(record.occurredAt()))
                .update();
    }

    public List<JobTimelineEventRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            stage,
                            status,
                            message,
                            duration_ms,
                            error_summary,
                            occurred_at
                        FROM job_timeline_events
                        WHERE job_id = :jobId
                        ORDER BY occurred_at ASC, id ASC
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    public Optional<JobTimelineEventRecord> findLatestByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            stage,
                            status,
                            message,
                            duration_ms,
                            error_summary,
                            occurred_at
                        FROM job_timeline_events
                        WHERE job_id = :jobId
                        ORDER BY occurred_at DESC, id DESC
                        LIMIT 1
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .optional();
    }

    public int deleteByJobId(String jobId) {
        return jdbcClient.sql("""
                        DELETE FROM job_timeline_events
                        WHERE job_id = :jobId
                        """)
                .param("jobId", jobId)
                .update();
    }

    private JobTimelineEventRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        long durationMs = rs.getLong("duration_ms");
        boolean durationMsWasNull = rs.wasNull();
        return new JobTimelineEventRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                LocalizationJobStage.valueOf(rs.getString("stage")),
                JobTimelineEventStatus.valueOf(rs.getString("status")),
                rs.getString("message"),
                durationMsWasNull ? null : durationMs,
                rs.getString("error_summary"),
                rs.getTimestamp("occurred_at").toInstant()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
