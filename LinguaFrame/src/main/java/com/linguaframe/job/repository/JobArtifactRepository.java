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
                            created_at
                        FROM job_artifacts
                        WHERE job_id = :jobId
                        ORDER BY created_at, id
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
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
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
