package com.linguaframe.job.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.entity.QualityEvaluationRecord;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class QualityEvaluationRepository {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public QualityEvaluationRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void save(QualityEvaluationRecord record) {
        jdbcClient.sql("""
                        INSERT INTO quality_evaluations (
                            id,
                            job_id,
                            language,
                            score,
                            verdict,
                            completeness,
                            readability,
                            timing_preservation,
                            naturalness,
                            issues_json,
                            suggested_fixes_json,
                            status,
                            safe_error_summary,
                            created_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :language,
                            :score,
                            :verdict,
                            :completeness,
                            :readability,
                            :timingPreservation,
                            :naturalness,
                            :issuesJson,
                            :suggestedFixesJson,
                            :status,
                            :safeErrorSummary,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("language", record.language())
                .param("score", record.score())
                .param("verdict", record.verdict())
                .param("completeness", record.completeness())
                .param("readability", record.readability())
                .param("timingPreservation", record.timingPreservation())
                .param("naturalness", record.naturalness())
                .param("issuesJson", writeStringList(record.issues()))
                .param("suggestedFixesJson", writeStringList(record.suggestedFixes()))
                .param("status", record.status().name())
                .param("safeErrorSummary", record.safeErrorSummary())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    public Optional<QualityEvaluationRecord> findLatestByJobIdAndLanguage(String jobId, String language) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            language,
                            score,
                            verdict,
                            completeness,
                            readability,
                            timing_preservation,
                            naturalness,
                            issues_json,
                            suggested_fixes_json,
                            status,
                            safe_error_summary,
                            created_at
                        FROM quality_evaluations
                        WHERE job_id = :jobId
                          AND language = :language
                        ORDER BY created_at DESC, id DESC
                        LIMIT 1
                        """)
                .param("jobId", jobId)
                .param("language", language)
                .query(this::mapRow)
                .optional();
    }

    public List<QualityEvaluationRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            language,
                            score,
                            verdict,
                            completeness,
                            readability,
                            timing_preservation,
                            naturalness,
                            issues_json,
                            suggested_fixes_json,
                            status,
                            safe_error_summary,
                            created_at
                        FROM quality_evaluations
                        WHERE job_id = :jobId
                        ORDER BY created_at, id
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    private QualityEvaluationRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new QualityEvaluationRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getString("language"),
                rs.getInt("score"),
                rs.getString("verdict"),
                rs.getInt("completeness"),
                rs.getInt("readability"),
                rs.getInt("timing_preservation"),
                rs.getInt("naturalness"),
                readStringList(rs.getString("issues_json")),
                readStringList(rs.getString("suggested_fixes_json")),
                QualityEvaluationStatus.valueOf(rs.getString("status")),
                rs.getString("safe_error_summary"),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private String writeStringList(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Quality evaluation text list could not be serialized.", ex);
        }
    }

    private List<String> readStringList(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Quality evaluation text list could not be deserialized.", ex);
        }
    }
}
