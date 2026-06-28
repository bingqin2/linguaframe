package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.RetentionJobCandidateVo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
                            tts_voice,
                            translation_style,
                            subtitle_style_preset,
                            translation_glossary_json,
                            translation_glossary_hash,
                            translation_glossary_entry_count,
                            subtitle_polishing_mode,
                            demo_profile_id,
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
                            :ttsVoice,
                            :translationStyle,
                            :subtitleStylePreset,
                            :translationGlossaryJson,
                            :translationGlossaryHash,
                            :translationGlossaryEntryCount,
                            :subtitlePolishingMode,
                            :demoProfileId,
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
                .param("ttsVoice", record.ttsVoice())
                .param("translationStyle", record.translationStyle())
                .param("subtitleStylePreset", record.subtitleStylePreset())
                .param("translationGlossaryJson", record.translationGlossaryJson())
                .param("translationGlossaryHash", record.translationGlossaryHash())
                .param("translationGlossaryEntryCount", record.translationGlossaryEntryCount())
                .param("subtitlePolishingMode", record.subtitlePolishingMode())
                .param("demoProfileId", record.demoProfileId())
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
                            tts_voice,
                            translation_style,
                            subtitle_style_preset,
                            translation_glossary_json,
                            translation_glossary_hash,
                            translation_glossary_entry_count,
                            subtitle_polishing_mode,
                            demo_profile_id,
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

    public List<LocalizationJobSummaryVo> findSummaries(
            LocalizationJobStatus status,
            int limit,
            int offset
    ) {
        String statusFilter = statusFilter(status);
        return jdbcClient.sql("""
                        SELECT
                            jobs.id AS job_id,
                            jobs.video_id,
                            videos.original_filename,
                            jobs.target_language,
                            jobs.tts_voice,
                            jobs.translation_style,
                            jobs.subtitle_style_preset,
                            jobs.translation_glossary_hash,
                            jobs.translation_glossary_entry_count,
                            jobs.subtitle_polishing_mode,
                            jobs.demo_profile_id,
                            jobs.status,
                            jobs.created_at,
                            jobs.started_at,
                            jobs.completed_at,
                            jobs.failed_at,
                            jobs.failure_stage,
                            jobs.failure_reason,
                            jobs.retry_count,
                            COALESCE(SUM(model_call_records.estimated_cost_usd), 0) AS estimated_cost_usd
                        FROM localization_jobs jobs
                        JOIN videos ON videos.id = jobs.video_id
                        LEFT JOIN model_call_records ON model_call_records.job_id = jobs.id
                        %s
                        GROUP BY
                            jobs.id,
                            jobs.video_id,
                            videos.original_filename,
                            jobs.target_language,
                            jobs.tts_voice,
                            jobs.translation_style,
                            jobs.subtitle_style_preset,
                            jobs.translation_glossary_hash,
                            jobs.translation_glossary_entry_count,
                            jobs.subtitle_polishing_mode,
                            jobs.demo_profile_id,
                            jobs.status,
                            jobs.created_at,
                            jobs.started_at,
                            jobs.completed_at,
                            jobs.failed_at,
                            jobs.failure_stage,
                            jobs.failure_reason,
                            jobs.retry_count
                        ORDER BY jobs.created_at DESC, jobs.id DESC
                        LIMIT :limit OFFSET :offset
                        """.formatted(statusFilter))
                .param("status", status == null ? null : status.name())
                .param("limit", limit)
                .param("offset", offset)
                .query(this::mapSummaryRow)
                .list();
    }

    public int countSummaries(LocalizationJobStatus status) {
        String statusFilter = statusFilter(status);
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM localization_jobs jobs
                        %s
                        """.formatted(statusFilter))
                .param("status", status == null ? null : status.name())
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    public List<RetentionJobCandidateVo> findRetentionCandidates(
            Set<LocalizationJobStatus> statuses,
            Instant cutoff,
            int limit
    ) {
        List<String> statusNames = statuses.stream()
                .map(Enum::name)
                .toList();
        return jdbcClient.sql("""
                        SELECT
                            id,
                            video_id,
                            status,
                            updated_at
                        FROM localization_jobs
                        WHERE status IN (:statuses)
                          AND updated_at < :cutoff
                        ORDER BY updated_at, id
                        LIMIT :limit
                        """)
                .param("statuses", statusNames)
                .param("cutoff", Timestamp.from(cutoff))
                .param("limit", limit)
                .query((rs, rowNum) -> new RetentionJobCandidateVo(
                        rs.getString("id"),
                        rs.getString("video_id"),
                        LocalizationJobStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("updated_at").toInstant()
                ))
                .list();
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

    public boolean markCancelled(String jobId, Instant cancelledAt) {
        int updated = jdbcClient.sql("""
                        UPDATE localization_jobs
                        SET status = :cancelledStatus,
                            completed_at = :completedAt,
                            failed_at = NULL,
                            failure_stage = NULL,
                            failure_reason = NULL,
                            updated_at = :updatedAt
                        WHERE id = :id
                          AND status IN (:queuedStatus, :retryingStatus, :processingStatus)
                        """)
                .param("cancelledStatus", LocalizationJobStatus.CANCELLED.name())
                .param("completedAt", Timestamp.from(cancelledAt))
                .param("updatedAt", Timestamp.from(cancelledAt))
                .param("id", jobId)
                .param("queuedStatus", LocalizationJobStatus.QUEUED.name())
                .param("retryingStatus", LocalizationJobStatus.RETRYING.name())
                .param("processingStatus", LocalizationJobStatus.PROCESSING.name())
                .update();
        return updated == 1;
    }

    public boolean isCancelled(String jobId) {
        Boolean cancelled = jdbcClient.sql("""
                        SELECT COUNT(*) > 0
                        FROM localization_jobs
                        WHERE id = :id
                          AND status = :cancelledStatus
                        """)
                .param("id", jobId)
                .param("cancelledStatus", LocalizationJobStatus.CANCELLED.name())
                .query(Boolean.class)
                .single();
        return Boolean.TRUE.equals(cancelled);
    }

    public int countByVideoId(String videoId) {
        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM localization_jobs
                        WHERE video_id = :videoId
                        """)
                .param("videoId", videoId)
                .query(Integer.class)
                .single();
        return count == null ? 0 : count;
    }

    public int deleteById(String jobId) {
        return jdbcClient.sql("""
                        DELETE FROM localization_jobs
                        WHERE id = :id
                        """)
                .param("id", jobId)
                .update();
    }

    private LocalizationJobRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        String failureStage = rs.getString("failure_stage");
        return new LocalizationJobRecord(
                rs.getString("id"),
                rs.getString("video_id"),
                rs.getString("target_language"),
                rs.getString("tts_voice"),
                rs.getString("translation_style"),
                rs.getString("subtitle_style_preset"),
                rs.getString("translation_glossary_json"),
                rs.getString("translation_glossary_hash"),
                rs.getInt("translation_glossary_entry_count"),
                rs.getString("subtitle_polishing_mode"),
                rs.getString("demo_profile_id"),
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

    private LocalizationJobSummaryVo mapSummaryRow(ResultSet rs, int rowNum) throws SQLException {
        String failureStage = rs.getString("failure_stage");
        BigDecimal estimatedCostUsd = rs.getBigDecimal("estimated_cost_usd");
        return new LocalizationJobSummaryVo(
                rs.getString("job_id"),
                rs.getString("video_id"),
                rs.getString("original_filename"),
                rs.getString("target_language"),
                rs.getString("tts_voice"),
                rs.getString("translation_style"),
                rs.getString("subtitle_style_preset"),
                rs.getInt("translation_glossary_entry_count"),
                rs.getString("translation_glossary_hash"),
                rs.getString("subtitle_polishing_mode"),
                rs.getString("demo_profile_id"),
                LocalizationJobStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                instantOrNull(rs.getTimestamp("started_at")),
                instantOrNull(rs.getTimestamp("completed_at")),
                instantOrNull(rs.getTimestamp("failed_at")),
                failureStage == null ? null : LocalizationJobStage.valueOf(failureStage),
                rs.getString("failure_reason"),
                rs.getInt("retry_count"),
                estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd
        );
    }

    private String statusFilter(LocalizationJobStatus status) {
        return status == null ? "" : "WHERE jobs.status = :status";
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
