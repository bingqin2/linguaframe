package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.SubtitleDraftSegmentRecord;
import com.linguaframe.job.domain.enums.SubtitleReviewDecision;
import com.linguaframe.job.domain.enums.SubtitleReviewIssueCategory;
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
class SubtitleDraftSegmentRepositoryTests {

    @Autowired
    private SubtitleDraftSegmentRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM subtitle_draft_segments").update();
    }

    @Test
    void upsertsAndReadsReviewAnnotations() {
        Instant createdAt = Instant.parse("2026-06-29T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-06-29T10:05:00Z");

        repository.upsert(new SubtitleDraftSegmentRecord(
                "draft-annotation-1",
                "job-annotation",
                "zh-CN",
                2,
                "审阅后的字幕",
                SubtitleReviewDecision.NEEDS_FOLLOWUP,
                List.of(SubtitleReviewIssueCategory.TIMING, SubtitleReviewIssueCategory.READABILITY),
                "Check timing before final handoff.",
                createdAt,
                updatedAt
        ));

        List<SubtitleDraftSegmentRecord> records = repository.findByJobIdAndLanguage("job-annotation", "zh-CN");

        assertThat(records).hasSize(1);
        SubtitleDraftSegmentRecord record = records.getFirst();
        assertThat(record.segmentIndex()).isEqualTo(2);
        assertThat(record.text()).isEqualTo("审阅后的字幕");
        assertThat(record.reviewDecision()).isEqualTo(SubtitleReviewDecision.NEEDS_FOLLOWUP);
        assertThat(record.issueCategories())
                .containsExactly(SubtitleReviewIssueCategory.TIMING, SubtitleReviewIssueCategory.READABILITY);
        assertThat(record.reviewerNote()).isEqualTo("Check timing before final handoff.");
        assertThat(record.createdAt()).isEqualTo(createdAt);
        assertThat(record.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void upsertUpdatesAnnotationsWithoutChangingCreatedAt() {
        Instant createdAt = Instant.parse("2026-06-29T10:00:00Z");
        repository.upsert(new SubtitleDraftSegmentRecord(
                "draft-annotation-2",
                "job-annotation",
                "zh-CN",
                0,
                "第一版",
                SubtitleReviewDecision.EDITED,
                List.of(SubtitleReviewIssueCategory.TERM),
                "Term fix.",
                createdAt,
                Instant.parse("2026-06-29T10:01:00Z")
        ));

        repository.upsert(new SubtitleDraftSegmentRecord(
                "draft-annotation-3",
                "job-annotation",
                "zh-CN",
                0,
                "第二版",
                SubtitleReviewDecision.ACCEPTED,
                List.of(),
                null,
                Instant.parse("2026-06-29T10:02:00Z"),
                Instant.parse("2026-06-29T10:03:00Z")
        ));

        SubtitleDraftSegmentRecord record = repository
                .findByJobIdAndLanguage("job-annotation", "zh-CN")
                .getFirst();

        assertThat(record.id()).isEqualTo("draft-annotation-2");
        assertThat(record.text()).isEqualTo("第二版");
        assertThat(record.reviewDecision()).isEqualTo(SubtitleReviewDecision.ACCEPTED);
        assertThat(record.issueCategories()).isEmpty();
        assertThat(record.reviewerNote()).isNull();
        assertThat(record.createdAt()).isEqualTo(createdAt);
        assertThat(record.updatedAt()).isEqualTo(Instant.parse("2026-06-29T10:03:00Z"));
    }
}
