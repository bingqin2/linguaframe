package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateTranslationCacheEntryCommand;
import com.linguaframe.job.domain.entity.TranslationCacheEntryRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Autowired;
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
public class TranslationCacheRepository {

    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public TranslationCacheRepository(JdbcClient jdbcClient) {
        this(jdbcClient, Clock.systemUTC());
    }

    public TranslationCacheRepository(JdbcClient jdbcClient, Clock clock) {
        this.jdbcClient = jdbcClient;
        this.clock = clock;
    }

    public Optional<TranslationCacheEntryRecord> findByCacheKey(String cacheKey) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cache_key,
                            source_hash,
                            target_language,
                            provider,
                            model,
                            prompt_version,
                            translation_glossary_hash,
                            response_json,
                            source_job_id,
                            created_at
                        FROM translation_cache_entries
                        WHERE cache_key = :cacheKey
                        """)
                .param("cacheKey", cacheKey)
                .query(this::mapRow)
                .optional();
    }

    public TranslationCacheEntryRecord saveIfAbsent(CreateTranslationCacheEntryCommand command) {
        Optional<TranslationCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        TranslationCacheEntryRecord record = new TranslationCacheEntryRecord(
                UUID.randomUUID().toString(),
                command.cacheKey(),
                command.sourceHash(),
                command.targetLanguage(),
                command.provider(),
                command.model(),
                command.promptVersion(),
                command.translationGlossaryHash(),
                command.responseJson(),
                command.sourceJobId(),
                Instant.now(clock)
        );
        try {
            jdbcClient.sql("""
                            INSERT INTO translation_cache_entries (
                                id,
                                cache_key,
                                source_hash,
                                target_language,
                                provider,
                                model,
                                prompt_version,
                                translation_glossary_hash,
                                response_json,
                                source_job_id,
                                created_at
                            )
                            VALUES (
                                :id,
                                :cacheKey,
                                :sourceHash,
                                :targetLanguage,
                                :provider,
                                :model,
                                :promptVersion,
                                :translationGlossaryHash,
                                :responseJson,
                                :sourceJobId,
                                :createdAt
                            )
                            """)
                    .param("id", record.id())
                    .param("cacheKey", record.cacheKey())
                    .param("sourceHash", record.sourceHash())
                    .param("targetLanguage", record.targetLanguage())
                    .param("provider", record.provider())
                    .param("model", record.model())
                    .param("promptVersion", record.promptVersion())
                    .param("translationGlossaryHash", record.translationGlossaryHash())
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

    private TranslationCacheEntryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new TranslationCacheEntryRecord(
                rs.getString("id"),
                rs.getString("cache_key"),
                rs.getString("source_hash"),
                rs.getString("target_language"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getString("translation_glossary_hash"),
                rs.getString("response_json"),
                rs.getString("source_job_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
