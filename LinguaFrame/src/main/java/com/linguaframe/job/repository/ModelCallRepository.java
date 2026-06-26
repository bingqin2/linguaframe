package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.ModelCallRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class ModelCallRepository {

    private final JdbcClient jdbcClient;

    public ModelCallRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(ModelCallRecord record) {
        jdbcClient.sql("""
                        INSERT INTO model_call_records (
                            id,
                            job_id,
                            stage,
                            operation,
                            provider,
                            model,
                            prompt_version,
                            status,
                            latency_ms,
                            input_tokens,
                            output_tokens,
                            audio_seconds,
                            character_count,
                            estimated_cost_usd,
                            safe_error_summary,
                            created_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :stage,
                            :operation,
                            :provider,
                            :model,
                            :promptVersion,
                            :status,
                            :latencyMs,
                            :inputTokens,
                            :outputTokens,
                            :audioSeconds,
                            :characterCount,
                            :estimatedCostUsd,
                            :safeErrorSummary,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("stage", record.stage().name())
                .param("operation", record.operation().name())
                .param("provider", record.provider().name())
                .param("model", record.model())
                .param("promptVersion", record.promptVersion())
                .param("status", record.status().name())
                .param("latencyMs", record.latencyMs())
                .param("inputTokens", record.inputTokens())
                .param("outputTokens", record.outputTokens())
                .param("audioSeconds", record.audioSeconds())
                .param("characterCount", record.characterCount())
                .param("estimatedCostUsd", record.estimatedCostUsd())
                .param("safeErrorSummary", record.safeErrorSummary())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    public List<ModelCallRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            stage,
                            operation,
                            provider,
                            model,
                            prompt_version,
                            status,
                            latency_ms,
                            input_tokens,
                            output_tokens,
                            audio_seconds,
                            character_count,
                            estimated_cost_usd,
                            safe_error_summary,
                            created_at
                        FROM model_call_records
                        WHERE job_id = :jobId
                        ORDER BY created_at, id
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    private ModelCallRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ModelCallRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                LocalizationJobStage.valueOf(rs.getString("stage")),
                ModelCallOperation.valueOf(rs.getString("operation")),
                ModelCallProvider.valueOf(rs.getString("provider")),
                rs.getString("model"),
                rs.getString("prompt_version"),
                ModelCallStatus.valueOf(rs.getString("status")),
                rs.getLong("latency_ms"),
                integerOrNull(rs, "input_tokens"),
                integerOrNull(rs, "output_tokens"),
                decimalOrNull(rs, "audio_seconds"),
                integerOrNull(rs, "character_count"),
                rs.getBigDecimal("estimated_cost_usd"),
                rs.getString("safe_error_summary"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal decimalOrNull(ResultSet rs, String column) throws SQLException {
        BigDecimal value = rs.getBigDecimal(column);
        return rs.wasNull() ? null : value;
    }
}
