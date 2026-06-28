package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class JobArtifactRepository {

    private final JdbcClient jdbcClient;

    public JobArtifactRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(JobArtifactRecord record) {
        jdbcClient.sql("""
                        INSERT INTO job_artifacts (
                            id,
                            job_id,
                            type,
                            object_key,
                            filename,
                            content_type,
                            size_bytes,
                            content_sha256,
                            cache_hit,
                            source_artifact_id,
                            created_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :type,
                            :objectKey,
                            :filename,
                            :contentType,
                            :sizeBytes,
                            :contentSha256,
                            :cacheHit,
                            :sourceArtifactId,
                            :createdAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("type", record.type().name())
                .param("objectKey", record.objectKey())
                .param("filename", record.filename())
                .param("contentType", record.contentType())
                .param("sizeBytes", record.sizeBytes())
                .param("contentSha256", record.contentSha256())
                .param("cacheHit", record.cacheHit())
                .param("sourceArtifactId", record.sourceArtifactId())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .update();
    }

    public Optional<JobArtifactRecord> findById(String artifactId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            type,
                            object_key,
                            filename,
                            content_type,
                            size_bytes,
                            content_sha256,
                            cache_hit,
                            source_artifact_id,
                            created_at
                        FROM job_artifacts
                        WHERE id = :id
                        """)
                .param("id", artifactId)
                .query(this::mapRow)
                .optional();
    }

    public List<JobArtifactRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            type,
                            object_key,
                            filename,
                            content_type,
                            size_bytes,
                            content_sha256,
                            cache_hit,
                            source_artifact_id,
                            created_at
                        FROM job_artifacts
                        WHERE job_id = :jobId
                        ORDER BY created_at, id
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    public Optional<JobArtifactRecord> findReusableArtifact(
            String videoId,
            String targetLanguage,
            JobArtifactType type,
            String subtitleStylePreset
    ) {
        return jdbcClient.sql("""
                        SELECT
                            artifact.id,
                            artifact.job_id,
                            artifact.type,
                            artifact.object_key,
                            artifact.filename,
                            artifact.content_type,
                            artifact.size_bytes,
                            artifact.content_sha256,
                            artifact.cache_hit,
                            artifact.source_artifact_id,
                            artifact.created_at
                        FROM job_artifacts artifact
                        JOIN localization_jobs job ON job.id = artifact.job_id
                        WHERE job.video_id = :videoId
                          AND job.target_language = :targetLanguage
                          AND artifact.type = :type
                          AND (:type <> 'BURNED_VIDEO' OR job.subtitle_style_preset = :subtitleStylePreset)
                          AND artifact.cache_hit = FALSE
                          AND artifact.content_sha256 <> ''
                        ORDER BY artifact.created_at DESC, artifact.id DESC
                        LIMIT 1
                        """)
                .param("videoId", videoId)
                .param("targetLanguage", targetLanguage)
                .param("type", type.name())
                .param("subtitleStylePreset", subtitleStylePreset == null ? "STANDARD" : subtitleStylePreset)
                .query(this::mapRow)
                .optional();
    }

    private JobArtifactRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new JobArtifactRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                JobArtifactType.valueOf(rs.getString("type")),
                rs.getString("object_key"),
                rs.getString("filename"),
                rs.getString("content_type"),
                rs.getLong("size_bytes"),
                rs.getString("content_sha256"),
                rs.getBoolean("cache_hit"),
                rs.getString("source_artifact_id"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
