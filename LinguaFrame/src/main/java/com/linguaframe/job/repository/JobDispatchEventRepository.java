package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.JobDispatchEventRecord;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.JobDispatchEventType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JobDispatchEventRepository {

    private static final int MAX_ERROR_LENGTH = 512;

    private final JdbcClient jdbcClient;

    public JobDispatchEventRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(JobDispatchEventRecord record) {
        jdbcClient.sql("""
                        INSERT INTO job_dispatch_events (
                            id,
                            job_id,
                            event_type,
                            payload_json,
                            status,
                            attempts,
                            next_attempt_at,
                            last_error,
                            dispatched_at,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :eventType,
                            :payloadJson,
                            :status,
                            :attempts,
                            :nextAttemptAt,
                            :lastError,
                            :dispatchedAt,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("eventType", record.eventType().name())
                .param("payloadJson", record.payloadJson())
                .param("status", record.status().name())
                .param("attempts", record.attempts())
                .param("nextAttemptAt", Timestamp.from(record.nextAttemptAt()))
                .param("lastError", record.lastError())
                .param("dispatchedAt", timestampOrNull(record.dispatchedAt()))
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    public Optional<JobDispatchEventRecord> findLatestByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            event_type,
                            payload_json,
                            status,
                            attempts,
                            next_attempt_at,
                            last_error,
                            dispatched_at,
                            created_at,
                            updated_at
                        FROM job_dispatch_events
                        WHERE job_id = :jobId
                        ORDER BY created_at DESC
                        LIMIT 1
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .optional();
    }

    public List<JobDispatchEventRecord> findReadyToDispatch(Instant now, int limit) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            event_type,
                            payload_json,
                            status,
                            attempts,
                            next_attempt_at,
                            last_error,
                            dispatched_at,
                            created_at,
                            updated_at
                        FROM job_dispatch_events
                        WHERE status IN (:pendingStatus, :failedStatus)
                          AND next_attempt_at <= :now
                        ORDER BY created_at ASC
                        LIMIT :limit
                        """)
                .param("pendingStatus", JobDispatchEventStatus.PENDING.name())
                .param("failedStatus", JobDispatchEventStatus.FAILED.name())
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query(this::mapRow)
                .list();
    }

    public void markDispatched(String eventId, Instant dispatchedAt) {
        jdbcClient.sql("""
                        UPDATE job_dispatch_events
                        SET status = :status,
                            dispatched_at = :dispatchedAt,
                            updated_at = :updatedAt,
                            last_error = NULL
                        WHERE id = :id
                        """)
                .param("status", JobDispatchEventStatus.DISPATCHED.name())
                .param("dispatchedAt", Timestamp.from(dispatchedAt))
                .param("updatedAt", Timestamp.from(dispatchedAt))
                .param("id", eventId)
                .update();
    }

    public void markFailed(String eventId, int attempts, Instant nextAttemptAt, String lastError, Instant updatedAt) {
        jdbcClient.sql("""
                        UPDATE job_dispatch_events
                        SET status = :status,
                            attempts = :attempts,
                            next_attempt_at = :nextAttemptAt,
                            last_error = :lastError,
                            updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("status", JobDispatchEventStatus.FAILED.name())
                .param("attempts", attempts)
                .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .param("lastError", truncate(lastError))
                .param("updatedAt", Timestamp.from(updatedAt))
                .param("id", eventId)
                .update();
    }

    private JobDispatchEventRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp dispatchedAt = rs.getTimestamp("dispatched_at");
        return new JobDispatchEventRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                JobDispatchEventType.valueOf(rs.getString("event_type")),
                rs.getString("payload_json"),
                JobDispatchEventStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                rs.getString("last_error"),
                dispatchedAt == null ? null : dispatchedAt.toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }
}
