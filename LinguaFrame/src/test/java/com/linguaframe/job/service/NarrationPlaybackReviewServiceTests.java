package com.linguaframe.job.service;

import com.linguaframe.job.domain.dto.UpdateNarrationPlaybackReviewSegmentDto;
import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;
import com.linguaframe.job.repository.NarrationPlaybackReviewRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationPlaybackReviewServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationPlaybackReviewServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-30T07:30:00Z"), ZoneOffset.UTC);

    @Test
    void returnsBlockedWhenNoNarrationSegmentsExist() {
        MutableReviewRepository reviewRepository = new MutableReviewRepository(List.of());
        NarrationPlaybackReviewVo review = service(List.of(), reviewRepository, List.of()).getReview("job-playback");

        assertThat(review.status()).isEqualTo("BLOCKED");
        assertThat(review.nextAction()).isEqualTo("Save narration segments before playback review.");
        assertThat(review.segmentCount()).isZero();
        assertThat(review.safeLinks()).extracting(link -> link.kind())
                .contains("NARRATION_PLAYBACK_REVIEW", "NARRATION_PLAYBACK_REVIEW_MARKDOWN");
        assertThat(review.safetyNotes()).anyMatch(note -> note.contains("metadata-only"));
    }

    @Test
    void returnsAttentionWhenSomeSegmentsNeedReviewOrRerender() {
        MutableReviewRepository reviewRepository = new MutableReviewRepository(List.of(
                record("job-playback", 0, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                record("job-playback", 1, NarrationPlaybackReviewDecision.NEEDS_RERENDER, List.of(NarrationPlaybackIssueCategory.MIX), "ducking note")
        ));

        NarrationPlaybackReviewVo review = service(segments(), reviewRepository, artifacts()).getReview("job-playback");

        assertThat(review.status()).isEqualTo("ATTENTION");
        assertThat(review.nextAction()).isEqualTo("Review or revise narration segments before handoff.");
        assertThat(review.segmentCount()).isEqualTo(3);
        assertThat(review.reviewedSegmentCount()).isEqualTo(2);
        assertThat(review.acceptedSegmentCount()).isEqualTo(1);
        assertThat(review.needsRerenderCount()).isEqualTo(1);
        assertThat(review.unreviewedSegmentCount()).isEqualTo(1);
        assertThat(review.audioReady()).isTrue();
        assertThat(review.videoReady()).isTrue();
        assertThat(review.segments()).extracting(segment -> segment.segmentIndex() + ":" + segment.decision())
                .containsExactly("0:ACCEPTED", "1:NEEDS_RERENDER", "2:UNREVIEWED");
        assertThat(review.segments()).extracting(segment -> segment.reviewerNotePresent())
                .containsExactly(false, true, false);
    }

    @Test
    void returnsReadyWhenAllSegmentsAccepted() {
        MutableReviewRepository reviewRepository = new MutableReviewRepository(List.of(
                record("job-playback", 0, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                record("job-playback", 1, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                record("job-playback", 2, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), "")
        ));

        NarrationPlaybackReviewVo review = service(segments(), reviewRepository, artifacts()).getReview("job-playback");

        assertThat(review.status()).isEqualTo("READY");
        assertThat(review.nextAction()).isEqualTo("Playback review is ready for narration handoff.");
        assertThat(review.acceptedSegmentCount()).isEqualTo(3);
        assertThat(review.unreviewedSegmentCount()).isZero();
        assertThat(review.issueCategoryCounts()).extracting(count -> count.category() + "=" + count.count())
                .contains("TIMING=0", "MIX=0");
    }

    @Test
    void updatesOneSegmentReviewAndNormalizesNote() {
        MutableReviewRepository reviewRepository = new MutableReviewRepository(List.of());
        NarrationPlaybackReviewService service = service(segments(), reviewRepository, artifacts());

        NarrationPlaybackReviewVo review = service.updateSegmentReview("job-playback", 1, new UpdateNarrationPlaybackReviewSegmentDto(
                NarrationPlaybackReviewDecision.NEEDS_EDIT,
                List.of(NarrationPlaybackIssueCategory.TEXT, NarrationPlaybackIssueCategory.VOICE),
                "  Needs a calmer voice.  "
        ));

        assertThat(review.needsEditCount()).isEqualTo(1);
        assertThat(review.segments().get(1).decision()).isEqualTo("NEEDS_EDIT");
        assertThat(review.segments().get(1).issueCategories()).containsExactly("TEXT", "VOICE");
        assertThat(review.segments().get(1).reviewerNotePresent()).isTrue();
        assertThat(reviewRepository.records).singleElement().satisfies(record -> {
            assertThat(record.segmentIndex()).isEqualTo(1);
            assertThat(record.reviewerNote()).isEqualTo("Needs a calmer voice.");
        });
    }

    @Test
    void rendersSafeMarkdownWithoutNarrationTextOrReviewerNotes() {
        MutableReviewRepository reviewRepository = new MutableReviewRepository(List.of(
                record("job-playback", 0, NarrationPlaybackReviewDecision.NEEDS_EDIT, List.of(NarrationPlaybackIssueCategory.TEXT), "do not leak note"),
                record("job-playback", 1, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                record("job-playback", 2, NarrationPlaybackReviewDecision.UNREVIEWED, List.of(), "")
        ));
        NarrationPlaybackReviewService service = service(segments(), reviewRepository, artifacts());

        String markdown = service.renderMarkdown("job-playback");

        assertThat(markdown).contains("# Narration Playback Review");
        assertThat(markdown).contains("- Status: ATTENTION");
        assertThat(markdown).contains("- Needs edit: 1");
        assertThat(markdown).contains("Segment 0");
        assertThat(markdown).doesNotContain("Explain the first scene");
        assertThat(markdown).doesNotContain("do not leak note");
        assertThat(markdown).doesNotContain("/Users/");
        assertThat(markdown).doesNotContain("sk-");
    }

    private static NarrationPlaybackReviewService service(
            List<NarrationSegmentRecord> segments,
            MutableReviewRepository reviewRepository,
            List<JobArtifactVo> artifacts
    ) {
        return new NarrationPlaybackReviewServiceImpl(
                new StaticNarrationSegmentRepository(segments),
                reviewRepository,
                new StaticJobArtifactService(artifacts),
                CLOCK
        );
    }

    private static List<NarrationSegmentRecord> segments() {
        Instant now = Instant.parse("2026-06-30T07:00:00Z");
        return List.of(
                segment(0, "15.000", "28.000", "Explain the first scene.", now),
                segment(1, "55.000", "70.000", "Explain the second scene.", now),
                segment(2, "90.000", "105.000", "Explain the final scene.", now)
        );
    }

    private static NarrationSegmentRecord segment(int index, String start, String end, String text, Instant now) {
        return new NarrationSegmentRecord(
                "segment-" + index,
                "job-playback",
                index,
                new BigDecimal(start),
                new BigDecimal(end),
                text,
                "alloy",
                null,
                null,
                null,
                now,
                now
        );
    }

    private static List<JobArtifactVo> artifacts() {
        return List.of(
                artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                artifact("video-1", JobArtifactType.NARRATED_VIDEO)
        );
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type) {
        return new JobArtifactVo(
                id,
                "job-playback",
                type,
                id + ".bin",
                type == JobArtifactType.NARRATED_VIDEO ? "video/mp4" : "audio/mpeg",
                32L,
                id + "-hash",
                false,
                null,
                Instant.parse("2026-06-30T07:10:00Z")
        );
    }

    private static NarrationPlaybackReviewRecord record(
            String jobId,
            int segmentIndex,
            NarrationPlaybackReviewDecision decision,
            List<NarrationPlaybackIssueCategory> categories,
            String note
    ) {
        Instant now = Instant.parse("2026-06-30T07:15:00Z");
        return new NarrationPlaybackReviewRecord(
                jobId + "-review-" + segmentIndex,
                jobId,
                segmentIndex,
                decision,
                categories,
                note,
                now,
                now
        );
    }

    private record StaticNarrationSegmentRepository(List<NarrationSegmentRecord> segments)
            implements NarrationSegmentRepository {
        @Override
        public void replaceSegments(String jobId, List<NarrationSegmentRecord> segments) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NarrationSegmentRecord> findByJobId(String jobId) {
            return segments;
        }

        @Override
        public void deleteByJobId(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MutableReviewRepository implements NarrationPlaybackReviewRepository {
        private final List<NarrationPlaybackReviewRecord> records = new ArrayList<>();

        private MutableReviewRepository(List<NarrationPlaybackReviewRecord> initialRecords) {
            records.addAll(initialRecords);
        }

        @Override
        public List<NarrationPlaybackReviewRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public Optional<NarrationPlaybackReviewRecord> findByJobIdAndSegmentIndex(String jobId, int segmentIndex) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId) && record.segmentIndex() == segmentIndex)
                    .findFirst();
        }

        @Override
        public NarrationPlaybackReviewRecord upsert(NarrationPlaybackReviewRecord record) {
            records.removeIf(existing -> existing.jobId().equals(record.jobId()) && existing.segmentIndex() == record.segmentIndex());
            records.add(record);
            return record;
        }

        @Override
        public void deleteByJobId(String jobId) {
            records.removeIf(record -> record.jobId().equals(jobId));
        }
    }

    private record StaticJobArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {
        @Override
        public JobArtifactVo createArtifact(com.linguaframe.job.domain.bo.CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, com.linguaframe.job.domain.entity.JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new com.linguaframe.job.domain.bo.StoredObjectResourceBo(artifactId, "application/octet-stream", 0, InputStream.nullInputStream());
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredArtifactArchiveBo openArtifactArchive(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
