package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;
import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NarrationPlaybackReviewRepositoryTests {

    @Autowired
    private NarrationPlaybackReviewRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM narration_playback_reviews").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
        insertJob("job-playback-review", "video-playback-review");
        insertJob("other-job", "video-playback-review-other");
    }

    @Test
    void upsertsAndReadsPlaybackReviewRowsByJobAndSegment() {
        Instant createdAt = Instant.parse("2026-06-30T07:00:00Z");
        repository.upsert(new NarrationPlaybackReviewRecord(
                "review-1",
                "job-playback-review",
                1,
                NarrationPlaybackReviewDecision.NEEDS_EDIT,
                List.of(NarrationPlaybackIssueCategory.TEXT, NarrationPlaybackIssueCategory.VOICE),
                "Needs tighter explanation.",
                createdAt,
                Instant.parse("2026-06-30T07:01:00Z")
        ));
        repository.upsert(new NarrationPlaybackReviewRecord(
                "review-2",
                "job-playback-review",
                0,
                NarrationPlaybackReviewDecision.ACCEPTED,
                List.of(),
                "",
                createdAt,
                Instant.parse("2026-06-30T07:02:00Z")
        ));

        List<NarrationPlaybackReviewRecord> records = repository.findByJobId("job-playback-review");

        assertThat(records)
                .extracting(record -> record.segmentIndex() + ":" + record.decision() + ":" + record.issueCategories())
                .containsExactly(
                        "0:ACCEPTED:[]",
                        "1:NEEDS_EDIT:[TEXT, VOICE]"
                );
        assertThat(repository.findByJobIdAndSegmentIndex("job-playback-review", 1))
                .hasValueSatisfying(record -> {
                    assertThat(record.id()).isEqualTo("review-1");
                    assertThat(record.reviewerNote()).isEqualTo("Needs tighter explanation.");
                    assertThat(record.createdAt()).isEqualTo(createdAt);
                });
    }

    @Test
    void upsertKeepsUniqueJobSegmentAndDeleteIsJobScoped() {
        repository.upsert(record("review-original", "job-playback-review", 0, NarrationPlaybackReviewDecision.NEEDS_RERENDER));
        repository.upsert(record("review-updated", "job-playback-review", 0, NarrationPlaybackReviewDecision.ACCEPTED));
        repository.upsert(record("review-other", "other-job", 0, NarrationPlaybackReviewDecision.NEEDS_EDIT));

        assertThat(repository.findByJobId("job-playback-review"))
                .extracting(NarrationPlaybackReviewRecord::id, NarrationPlaybackReviewRecord::decision)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("review-updated", NarrationPlaybackReviewDecision.ACCEPTED));

        repository.deleteByJobId("job-playback-review");

        assertThat(repository.findByJobId("job-playback-review")).isEmpty();
        assertThat(repository.findByJobId("other-job")).hasSize(1);
    }

    private NarrationPlaybackReviewRecord record(
            String id,
            String jobId,
            int segmentIndex,
            NarrationPlaybackReviewDecision decision
    ) {
        Instant now = Instant.parse("2026-06-30T07:00:00Z");
        return new NarrationPlaybackReviewRecord(
                id,
                jobId,
                segmentIndex,
                decision,
                List.of(NarrationPlaybackIssueCategory.TIMING),
                "",
                now,
                now
        );
    }

    private void insertJob(String jobId, String videoId) {
        Instant now = Instant.parse("2026-06-30T07:00:00Z");
        jdbcClient.sql("""
                        INSERT INTO videos (
                            id,
                            original_filename,
                            content_type,
                            file_size_bytes,
                            source_object_key,
                            status,
                            created_at
                        )
                        VALUES (
                            :videoId,
                            :filename,
                            'video/mp4',
                            1024,
                            :objectKey,
                            'UPLOADED',
                            :createdAt
                        )
                        """)
                .param("videoId", videoId)
                .param("filename", videoId + ".mp4")
                .param("objectKey", "uploads/" + videoId + ".mp4")
                .param("createdAt", now)
                .update();
        jdbcClient.sql("""
                        INSERT INTO localization_jobs (
                            id,
                            video_id,
                            target_language,
                            status,
                            created_at,
                            updated_at
                        )
                        VALUES (
                            :jobId,
                            :videoId,
                            'zh-CN',
                            'QUEUED',
                            :createdAt,
                            :createdAt
                        )
                        """)
                .param("jobId", jobId)
                .param("videoId", videoId)
                .param("createdAt", now)
                .update();
    }
}
