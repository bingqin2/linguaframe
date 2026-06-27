package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateQualityEvaluationCacheEntryCommand;
import com.linguaframe.job.domain.entity.QualityEvaluationCacheEntryRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QualityEvaluationCacheRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public QualityEvaluationCacheRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    public QualityEvaluationCacheRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public Optional<QualityEvaluationCacheEntryRecord> findByCacheKey(String cacheKey) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cache_key,
                            source_hash,
                            target_hash,
                            language,
                            provider,
                            model,
                            prompt_version,
                            response_json,
                            source_job_id,
                            created_at
                        FROM quality_evaluation_cache_entries
                        WHERE cache_key = :cacheKey
                        """)
                .param("cacheKey", cacheKey)
                .query(this::mapRow)
                .optional();
    }

    public QualityEvaluationCacheEntryRecord saveIfAbsent(CreateQualityEvaluationCacheEntryCommand command) {
        Optional<QualityEvaluationCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        QualityEvaluationCacheEntryRecord record = new QualityEvaluationCacheEntryRecord(
                UUID.randomUUID().toString(),
                command.cacheKey(),
                command.sourceHash(),
                command.targetHash(),
                command.language(),
                command.provider(),
                command.model(),
                command.promptVersion(),
                command.responseJson(),
                command.sourceJobId(),
                Instant.now(clock)
        );
        try {
            jdbcClient.sql("""
                            INSERT INTO quality_evaluation_cache_entries (
                                id,
                                cache_key,
                                source_hash,
                                target_hash,
                                language,
                                provider,
                                model,
                                prompt_version,
                                response_json,
                                source_job_id,
                                created_at
                            )
                            VALUES (
                                :id,
                                :cacheKey,
                                :sourceHash,
                                :targetHash,
                                :language,
                                :provider,
                                :model,
                                :promptVersion,
                                :responseJson,
                                :sourceJobId,
                                :createdAt
                            )
                            """)
                    .param("id", record.id())
                    .param("cacheKey", record.cacheKey())
                    .param("sourceHash", record.sourceHash())
                    .param("targetHash", record.targetHash())
                    .param("language", record.language())
                    .param("provider", record.provider())
                    .param("model", record.model())
                    .param("promptVersion", record.promptVersion())
                    .param("responseJson", record.responseJson())
                    .param("sourceJobId", record.sourceJobId())
                    .param("createdAt", Timestamp.from(record.createdAt()))
                    .update();
            return record;
        } catch (DuplicateKeyException ex) {
            return findByCacheKey(command.cacheKey())
                    .orElseThrow(() -> ex);
        }
    }

    private QualityEvaluationCacheEntryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new QualityEvaluationCacheEntryRecord(
                rs.getString("id"),
                rs.getString("cache_key"),
                rs.getString("source_hash"),
                rs.getString("target_hash"),
                rs.getString("language"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getString("response_json"),
                rs.getString("source_job_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
