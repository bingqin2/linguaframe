package com.linguaframe.job.repository;

import com.linguaframe.job.domain.bo.CreateSubtitlePolishingCacheEntryCommand;
import com.linguaframe.job.domain.entity.SubtitlePolishingCacheEntryRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SubtitlePolishingCacheRepository {

    private final JdbcClient jdbcClient;

    public SubtitlePolishingCacheRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<SubtitlePolishingCacheEntryRecord> findByCacheKey(String cacheKey) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            cache_key,
                            source_hash,
                            target_language,
                            provider,
                            model,
                            prompt_version,
                            subtitle_polishing_mode,
                            response_json,
                            source_job_id,
                            created_at
                        FROM subtitle_polishing_cache_entries
                        WHERE cache_key = :cacheKey
                        """)
                .param("cacheKey", cacheKey)
                .query(this::mapRow)
                .optional();
    }

    public SubtitlePolishingCacheEntryRecord saveIfAbsent(CreateSubtitlePolishingCacheEntryCommand command) {
        Optional<SubtitlePolishingCacheEntryRecord> existing = findByCacheKey(command.cacheKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        jdbcClient.sql("""
                        INSERT INTO subtitle_polishing_cache_entries (
                            id,
                            cache_key,
                            source_hash,
                            target_language,
                            provider,
                            model,
                            prompt_version,
                            subtitle_polishing_mode,
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
                            :subtitlePolishingMode,
                            :responseJson,
                            :sourceJobId,
                            :createdAt
                        )
                        """)
                .param("id", id)
                .param("cacheKey", command.cacheKey())
                .param("sourceHash", command.sourceHash())
                .param("targetLanguage", command.targetLanguage())
                .param("provider", command.provider())
                .param("model", command.model())
                .param("promptVersion", command.promptVersion())
                .param("subtitlePolishingMode", command.subtitlePolishingMode())
                .param("responseJson", command.responseJson())
                .param("sourceJobId", command.sourceJobId())
                .param("createdAt", Timestamp.from(createdAt))
                .update();
        return findByCacheKey(command.cacheKey()).orElseThrow();
    }

    private SubtitlePolishingCacheEntryRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SubtitlePolishingCacheEntryRecord(
                rs.getString("id"),
                rs.getString("cache_key"),
                rs.getString("source_hash"),
                rs.getString("target_language"),
                rs.getString("provider"),
                rs.getString("model"),
                rs.getString("prompt_version"),
                rs.getString("subtitle_polishing_mode"),
                rs.getString("response_json"),
                rs.getString("source_job_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
