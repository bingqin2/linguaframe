package com.linguaframe.media.repository;

import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
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
                            source_content_sha256,
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
                            :sourceContentSha256,
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
                .param("sourceContentSha256", record.sourceContentSha256())
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
                            source_content_sha256,
                            source_object_key,
                            status,
                            created_at
                        FROM videos
                        WHERE id = :id
                        """)
                .param("id", id)
                .query((rs, rowNum) -> mapRow(rs))
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
                            source_content_sha256,
                            source_object_key,
                            status,
                            created_at
                        FROM videos
                        WHERE id = :id
                          AND owner_id = :ownerId
                        """)
                .param("id", id)
                .param("ownerId", ownerId)
                .query((rs, rowNum) -> mapRow(rs))
                .optional();
    }

    public List<VideoRecord> findRecentByOwnerIdAndSourceContentSha256(
            String ownerId,
            String sourceContentSha256,
            int limit
    ) {
        if (sourceContentSha256 == null || sourceContentSha256.isBlank() || limit <= 0) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT
                            id,
                            owner_id,
                            original_filename,
                            content_type,
                            file_size_bytes,
                            duration_seconds,
                            source_content_sha256,
                            source_object_key,
                            status,
                            created_at
                        FROM videos
                        WHERE owner_id = :ownerId
                          AND source_content_sha256 = :sourceContentSha256
                        ORDER BY created_at DESC, id DESC
                        LIMIT :limit
                        """)
                .param("ownerId", ownerId)
                .param("sourceContentSha256", sourceContentSha256)
                .param("limit", limit)
                .query((rs, rowNum) -> mapRow(rs))
                .list();
    }

    public int deleteById(String id) {
        return jdbcClient.sql("""
                        DELETE FROM videos
                        WHERE id = :id
                        """)
                .param("id", id)
                .update();
    }

    private VideoRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new VideoRecord(
                rs.getString("id"),
                rs.getString("owner_id"),
                rs.getString("original_filename"),
                rs.getString("content_type"),
                rs.getLong("file_size_bytes"),
                rs.getObject("duration_seconds", Integer.class),
                rs.getString("source_content_sha256"),
                rs.getString("source_object_key"),
                MediaUploadStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
