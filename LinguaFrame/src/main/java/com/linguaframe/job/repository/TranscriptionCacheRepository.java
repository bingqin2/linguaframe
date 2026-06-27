package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateTranscriptionCacheEntryCommand;
import com.linguaframe.job.domain.entity.TranscriptionCacheEntryRecord;
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
public class TranscriptionCacheRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public TranscriptionCacheRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    public TranscriptionCacheRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public Optional<TranscriptionCacheEntryRecord> findByCacheKey(String cacheKey) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cache_key,
                            audio_hash,
                            provider,
                            model,
                            prompt_version,
                            response_json,
                            source_job_id,
                            created_at
                        FROM transcription_cache_entries
                        WHERE cache_key = :cacheKey
                        """)
                .param("cacheKey", cacheKey)
                .query(this::mapRow)
                .optional();
    }

    public TranscriptionCacheEntryRecord saveIfAbsent(CreateTranscriptionCacheEntryCommand command) {
        Optional<TranscriptionCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        TranscriptionCacheEntryRecord record = new TranscriptionCacheEntryRecord(
                UUID.randomUUID().toString(),
                command.cacheKey(),
                command.audioHash(),
                command.provider(),
                command.model(),
                command.promptVersion(),
                command.responseJson(),
                command.sourceJobId(),
                Instant.now(clock)
        );
        try {
            jdbcClient.sql("""
                            INSERT INTO transcription_cache_entries (
                                id,
                                cache_key,
                                audio_hash,
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
                                :audioHash,
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
                    .param("audioHash", record.audioHash())
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

    private TranscriptionCacheEntryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TranscriptionCacheEntryRecord(
                rs.getString("id"),
                rs.getString("cache_key"),
                rs.getString("audio_hash"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getString("response_json"),
                rs.getString("source_job_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
