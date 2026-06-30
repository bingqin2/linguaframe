package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;
import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcNarrationPlaybackReviewRepository implements NarrationPlaybackReviewRepository {

    private final JdbcClient jdbcClient;

    public JdbcNarrationPlaybackReviewRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<NarrationPlaybackReviewRecord> findByJobId(String jobId) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            segment_index,
                            decision,
                            issue_categories,
                            reviewer_note,
                            created_at,
                            updated_at
                        FROM narration_playback_reviews
                        WHERE job_id = :jobId
                        ORDER BY segment_index
                        """)
                .param("jobId", jobId)
                .query(this::mapRow)
                .list();
    }

    @Override
    public Optional<NarrationPlaybackReviewRecord> findByJobIdAndSegmentIndex(String jobId, int segmentIndex) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            segment_index,
                            decision,
                            issue_categories,
                            reviewer_note,
                            created_at,
                            updated_at
                        FROM narration_playback_reviews
                        WHERE job_id = :jobId
                          AND segment_index = :segmentIndex
                        """)
                .param("jobId", jobId)
                .param("segmentIndex", segmentIndex)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public NarrationPlaybackReviewRecord upsert(NarrationPlaybackReviewRecord record) {
        jdbcClient.sql("""
                        MERGE INTO narration_playback_reviews (
                            id,
                            job_id,
                            segment_index,
                            decision,
                            issue_categories,
                            reviewer_note,
                            created_at,
                            updated_at
                        )
                        KEY (job_id, segment_index)
                        VALUES (
                            :id,
                            :jobId,
                            :segmentIndex,
                            :decision,
                            :issueCategories,
                            :reviewerNote,
                            :createdAt,
                            :updatedAt
                        )
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("segmentIndex", record.segmentIndex())
                .param("decision", record.decision().name())
                .param("issueCategories", serializeCategories(record.issueCategories()))
                .param("reviewerNote", record.reviewerNote())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
        return record;
    }

    @Override
    public void deleteByJobId(String jobId) {
        jdbcClient.sql("DELETE FROM narration_playback_reviews WHERE job_id = :jobId")
                .param("jobId", jobId)
                .update();
    }

    private NarrationPlaybackReviewRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new NarrationPlaybackReviewRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getInt("segment_index"),
                NarrationPlaybackReviewDecision.valueOf(rs.getString("decision")),
                parseCategories(rs.getString("issue_categories")),
                rs.getString("reviewer_note"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String serializeCategories(List<NarrationPlaybackIssueCategory> categories) {
        return categories == null || categories.isEmpty()
                ? ""
                : String.join(",", categories.stream().map(Enum::name).toList());
    }

    private List<NarrationPlaybackIssueCategory> parseCategories(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .filter(item -> !item.isBlank())
                .map(NarrationPlaybackIssueCategory::valueOf)
                .toList();
    }
}
