package com.linguaframe.media.repository;

import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class VideoRepository {

    private final JdbcClient jdbcClient;

    public VideoRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(VideoRecord record) {
        jdbcClient.sql("""
                        INSERT INTO videos (
                            id,
                            owner_id,
                            original_filename,
                            content_type,
                            file_size_bytes,
                            duration_seconds,
                            source_object_key,
                            status,
                            created_at
                        )
                        VALUES (
                            :id,
                            :ownerId,
                            :originalFilename,
                            :contentType,
                            :fileSizeBytes,
                            :durationSeconds,
                            :sourceObjectKey,
                            :status,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("ownerId", record.ownerId())
                .param("originalFilename", record.originalFilename())
                .param("contentType", record.contentType())
                .param("fileSizeBytes", record.fileSizeBytes())
                .param("durationSeconds", record.durationSeconds())
                .param("sourceObjectKey", record.sourceObjectKey())
                .param("status", record.status().name())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    public Optional<VideoRecord> findById(String id) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            owner_id,
                            original_filename,
                            content_type,
                            file_size_bytes,
                            duration_seconds,
                            source_object_key,
                            status,
                            created_at
                        FROM videos
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> new VideoRecord(
                        rs.getString("id"),
                        rs.getString("owner_id"),
                        rs.getString("original_filename"),
                        rs.getString("content_type"),
                        rs.getLong("file_size_bytes"),
                        rs.getObject("duration_seconds", Integer.class),
                        rs.getString("source_object_key"),
                        MediaUploadStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    public Optional<VideoRecord> findByIdAndOwnerId(String id, String ownerId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            owner_id,
                            original_filename,
                            content_type,
                            file_size_bytes,
                            duration_seconds,
                            source_object_key,
                            status,
                            created_at
                        FROM videos
                        WHERE id = :id
                          AND owner_id = :ownerId
                        """)
                .param("id", id)
                .param("ownerId", ownerId)
                .query((rs, rowNum) -> new VideoRecord(
                        rs.getString("id"),
                        rs.getString("owner_id"),
                        rs.getString("original_filename"),
                        rs.getString("content_type"),
                        rs.getLong("file_size_bytes"),
                        rs.getObject("duration_seconds", Integer.class),
                        rs.getString("source_object_key"),
                        MediaUploadStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    public int deleteById(String id) {
        return jdbcClient.sql("""
                        DELETE FROM videos
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }
}
