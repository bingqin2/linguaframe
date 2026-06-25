package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class LocalizationJobRepository {

    private static final int MAX_FAILURE_REASON_LENGTH = 512;

    private final JdbcClient jdbcClient;

    public LocalizationJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(LocalizationJobRecord record) {
        jdbcClient.sql("""
                        INSERT INTO localization_jobs (
                            id,
                            video_id,
                            target_language,
                            status,
                            created_at,
                            started_at,
                            completed_at,
                            failed_at,
                            failure_stage,
                            failure_reason,
                            retry_count,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :videoId,
                            :targetLanguage,
                            :status,
                            :createdAt,
                            :startedAt,
                            :completedAt,
                            :failedAt,
                            :failureStage,
                            :failureReason,
                            :retryCount,
                            :updatedAt
                        )
                        """)
                .param("id", record.id())
                .param("videoId", record.videoId())
                .param("targetLanguage", record.targetLanguage())
                .param("status", record.status().name())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("startedAt", timestampOrNull(record.startedAt()))
                .param("completedAt", timestampOrNull(record.completedAt()))
                .param("failedAt", timestampOrNull(record.failedAt()))
                .param("failureStage", record.failureStage() == null ? null : record.failureStage().name())
                .param("failureReason", truncate(record.failureReason()))
                .param("retryCount", record.retryCount())
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    public Optional<LocalizationJobRecord> findById(String id) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            video_id,
                            target_language,
                            status,
                            created_at,
                            started_at,
                            completed_at,
                            failed_at,
                            failure_stage,
                            failure_reason,
                            retry_count,
                            updated_at
                        FROM localization_jobs
                        WHERE id = :id
                        """)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    public boolean claimForExecution(String jobId, Instant now) {
        int updated = jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET status = :processingStatus,
                            started_at = COALESCE(started_at, :now),
                            completed_at = NULL,
                            failed_at = NULL,
                            failure_stage = NULL,
                            failure_reason = NULL,
                            updated_at = :now
                        WHERE id = :id
                          AND status IN (:queuedStatus, :retryingStatus)
                        """)
                .param("processingStatus", LocalizationJobStatus.PROCESSING.name())
                .param("now", Timestamp.from(now))
                .param("id", jobId)
                .param("queuedStatus", LocalizationJobStatus.QUEUED.name())
                .param("retryingStatus", LocalizationJobStatus.RETRYING.name())
                .update();
        return updated == 1;
    }

    public void markCompleted(String jobId, Instant completedAt) {
        jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET status = :status,
                            completed_at = :completedAt,
                            failed_at = NULL,
                            failure_stage = NULL,
                            failure_reason = NULL,
                            updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("status", LocalizationJobStatus.COMPLETED.name())
                .param("completedAt", Timestamp.from(completedAt))
                .param("updatedAt", Timestamp.from(completedAt))
                .param("id", jobId)
                .update();
    }

    public void markFailed(String jobId, LocalizationJobStage stage, String failureReason, Instant failedAt) {
        jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET status = :status,
                            failed_at = :failedAt,
                            failure_stage = :failureStage,
                            failure_reason = :failureReason,
                            updated_at = :updatedAt
                        WHERE id = :id
                        """)
                .param("status", LocalizationJobStatus.FAILED.name())
                .param("failedAt", Timestamp.from(failedAt))
                .param("failureStage", stage.name())
                .param("failureReason", truncate(failureReason))
                .param("updatedAt", Timestamp.from(failedAt))
                .param("id", jobId)
                .update();
    }

    public boolean markRetrying(String jobId, Instant now) {
        int updated = jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET status = :retryingStatus,
                            retry_count = retry_count + 1,
                            started_at = NULL,
                            completed_at = NULL,
                            failed_at = NULL,
                            failure_stage = NULL,
                            failure_reason = NULL,
                            updated_at = :updatedAt
                        WHERE id = :id
                          AND status = :failedStatus
                        """)
                .param("retryingStatus", LocalizationJobStatus.RETRYING.name())
                .param("updatedAt", Timestamp.from(now))
                .param("id", jobId)
                .param("failedStatus", LocalizationJobStatus.FAILED.name())
                .update();
        return updated == 1;
    }

    private LocalizationJobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String failureStage = rs.getString("failure_stage");
        return new LocalizationJobRecord(
                rs.getString("id"),
                rs.getString("video_id"),
                rs.getString("target_language"),
                LocalizationJobStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                instantOrNull(rs.getTimestamp("started_at")),
                instantOrNull(rs.getTimestamp("completed_at")),
                instantOrNull(rs.getTimestamp("failed_at")),
                failureStage == null ? null : LocalizationJobStage.valueOf(failureStage),
                rs.getString("failure_reason"),
                rs.getInt("retry_count"),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private Timestamp timestampOrNull(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_FAILURE_REASON_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_FAILURE_REASON_LENGTH);
    }
}
