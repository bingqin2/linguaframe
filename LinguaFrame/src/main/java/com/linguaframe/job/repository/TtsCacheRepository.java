package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateTtsCacheEntryCommand;
import com.linguaframe.job.domain.entity.TtsCacheEntryRecord;
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
public class TtsCacheRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public TtsCacheRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    public TtsCacheRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public Optional<TtsCacheEntryRecord> findByCacheKey(String cacheKey) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cache_key,
                            text_hash,
                            language,
                            provider,
                            model,
                            voice,
                            response_json,
                            source_job_id,
                            created_at
                        FROM tts_cache_entries
                        WHERE cache_key = :cacheKey
                        """)
                .param("cacheKey", cacheKey)
                .query(this::mapRow)
                .optional();
    }

    public TtsCacheEntryRecord saveIfAbsent(CreateTtsCacheEntryCommand command) {
        Optional<TtsCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        TtsCacheEntryRecord record = new TtsCacheEntryRecord(
                UUID.randomUUID().toString(),
                command.cacheKey(),
                command.textHash(),
                command.language(),
                command.provider(),
                command.model(),
                command.voice(),
                command.responseJson(),
                command.sourceJobId(),
                Instant.now(clock)
        );
        try {
            jdbcClient.sql("""
                            INSERT INTO tts_cache_entries (
                                id,
                                cache_key,
                                text_hash,
                                language,
                                provider,
                                model,
                                voice,
                                response_json,
                                source_job_id,
                                created_at
                            )
                            VALUES (
                                :id,
                                :cacheKey,
                                :textHash,
                                :language,
                                :provider,
                                :model,
                                :voice,
                                :responseJson,
                                :sourceJobId,
                                :createdAt
                            )
                            """)
                    .param("id", record.id())
                    .param("cacheKey", record.cacheKey())
                    .param("textHash", record.textHash())
                    .param("language", record.language())
                    .param("provider", record.provider())
                    .param("model", record.model())
                    .param("voice", record.voice())
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

    private TtsCacheEntryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TtsCacheEntryRecord(
                rs.getString("id"),
                rs.getString("cache_key"),
                rs.getString("text_hash"),
                rs.getString("language"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("voice"),
                rs.getString("response_json"),
                rs.getString("source_job_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
