package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class LocalizationJobRepository {

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
                            created_at
                        )
                        VALUES (
                            :id,
                            :videoId,
                            :targetLanguage,
                            :status,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("videoId", record.videoId())
                .param("targetLanguage", record.targetLanguage())
                .param("status", record.status().name())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    public Optional<LocalizationJobRecord> findById(String id) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            video_id,
                            target_language,
                            status,
                            created_at
                        FROM localization_jobs
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new LocalizationJobRecord(
                        rs.getString("id"),
                        rs.getString("video_id"),
                        rs.getString("target_language"),
                        LocalizationJobStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }
}
