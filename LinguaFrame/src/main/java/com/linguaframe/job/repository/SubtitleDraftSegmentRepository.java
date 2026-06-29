package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.SubtitleDraftSegmentRecord;
import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class SubtitleDraftSegmentRepository {

    private final JdbcClient jdbcClient;

    public SubtitleDraftSegmentRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SubtitleDraftSegmentRecord> findByJobIdAndLanguage(String jobId, String language) {
        return jdbcClient.sql("""
                        SELECT
                            id,
                            job_id,
                            language,
                            segment_index,
                            text,
                            review_decision,
                            issue_categories,
                            reviewer_note,
                            created_at,
                            updated_at
                        FROM subtitle_draft_segments
                        WHERE job_id = :jobId AND language = :language
                        ORDER BY segment_index
                        """)
                .param("jobId", jobId)
                .param("language", language)
                .query(this::mapRow)
                .list();
    }

    public void upsert(SubtitleDraftSegmentRecord record) {
        jdbcClient.sql("""
                        INSERT INTO subtitle_draft_segments (
                            id,
                            job_id,
                            language,
                            segment_index,
                            text,
                            review_decision,
                            issue_categories,
                            reviewer_note,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :id,
                            :jobId,
                            :language,
                            :segmentIndex,
                            :text,
                            :reviewDecision,
                            :issueCategories,
                            :reviewerNote,
                            :createdAt,
                            :updatedAt
                        )
                        ON DUPLICATE KEY UPDATE
                            text = VALUES(text),
                            review_decision = VALUES(review_decision),
                            issue_categories = VALUES(issue_categories),
                            reviewer_note = VALUES(reviewer_note),
                            updated_at = VALUES(updated_at)
                        """)
                .param("id", record.id())
                .param("jobId", record.jobId())
                .param("language", record.language())
                .param("segmentIndex", record.segmentIndex())
                .param("text", record.text())
                .param("reviewDecision", normalizeDecision(record.reviewDecision()).name())
                .param("issueCategories", serializeIssueCategories(record.issueCategories()))
                .param("reviewerNote", record.reviewerNote())
                .param("createdAt", Timestamp.from(record.createdAt()))
                .param("updatedAt", Timestamp.from(record.updatedAt()))
                .update();
    }

    public void deleteByJobIdAndLanguage(String jobId, String language) {
        jdbcClient.sql("DELETE FROM subtitle_draft_segments WHERE job_id = :jobId AND language = :language")
                .param("jobId", jobId)
                .param("language", language)
                .update();
    }

    private SubtitleDraftSegmentRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new SubtitleDraftSegmentRecord(
                rs.getString("id"),
                rs.getString("job_id"),
                rs.getString("language"),
                rs.getInt("segment_index"),
                rs.getString("text"),
                parseDecision(rs.getString("review_decision")),
                parseIssueCategories(rs.getString("issue_categories")),
                rs.getString("reviewer_note"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private SubtitleReviewDecision parseDecision(String value) {
        if (value == null || value.isBlank()) {
            return SubtitleReviewDecision.EDITED;
        }
        return SubtitleReviewDecision.valueOf(value.trim());
    }

    private SubtitleReviewDecision normalizeDecision(SubtitleReviewDecision decision) {
        return decision == null ? SubtitleReviewDecision.EDITED : decision;
    }

    private List<SubtitleReviewIssueCategory> parseIssueCategories(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .map(SubtitleReviewIssueCategory::valueOf)
                .toList();
    }

    private String serializeIssueCategories(List<SubtitleReviewIssueCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        return categories.stream()
                .map(SubtitleReviewIssueCategory::name)
                .collect(Collectors.joining(","));
    }
}
