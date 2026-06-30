package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.bo.StoredArtifactArchiveBo;
import com.linguaframe.job.domain.bo.StoredObjectResourceBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.repository.NarrationPlaybackReviewRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.impl.NarrationPlaybackReviewResolutionServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class NarrationPlaybackReviewResolutionServiceTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-30T08:30:00Z"), ZoneOffset.UTC);

    @Test
    void returnsBlockedWhenNoNarrationSegmentsExist() {
        NarrationPlaybackReviewResolutionVo resolution = service(List.of(), List.of(), List.of()).getResolution("job-resolution");

        assertThat(resolution.status()).isEqualTo("BLOCKED");
        assertThat(resolution.nextAction()).isEqualTo("Save narration segments before resolving playback review.");
        assertThat(resolution.segmentCount()).isZero();
        assertThat(resolution.unresolvedSegmentCount()).isZero();
        assertThat(resolution.safeLinks()).extracting(link -> link.kind())
                .contains("NARRATION_PLAYBACK_RESOLUTION", "NARRATION_PLAYBACK_RESOLUTION_MARKDOWN");
    }

    @Test
    void returnsAttentionWithSafeActionsForUnresolvedSegments() {
        NarrationPlaybackReviewResolutionVo resolution = service(
                segments(),
                List.of(
                        record(0, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                        record(1, NarrationPlaybackReviewDecision.NEEDS_EDIT, List.of(NarrationPlaybackIssueCategory.TEXT, NarrationPlaybackIssueCategory.VOICE), "do not leak note"),
                        record(2, NarrationPlaybackReviewDecision.NEEDS_RERENDER, List.of(NarrationPlaybackIssueCategory.MIX), "rerender note")
                ),
                List.of(artifact("audio-1", JobArtifactType.NARRATION_AUDIO))
        ).getResolution("job-resolution");

        assertThat(resolution.status()).isEqualTo("ATTENTION");
        assertThat(resolution.nextAction()).isEqualTo("Resolve playback review issues, save narration edits, and regenerate narration media before handoff.");
        assertThat(resolution.segmentCount()).isEqualTo(4);
        assertThat(resolution.readySegmentCount()).isEqualTo(1);
        assertThat(resolution.textRevisionRequiredCount()).isEqualTo(1);
        assertThat(resolution.rerenderRequiredCount()).isEqualTo(1);
        assertThat(resolution.unreviewedSegmentCount()).isEqualTo(1);
        assertThat(resolution.audioReady()).isTrue();
        assertThat(resolution.videoReady()).isFalse();
        assertThat(resolution.unresolvedSegments())
                .extracting(segment -> segment.segmentIndex() + ":" + segment.resolutionStatus() + ":" + segment.nextAction())
                .containsExactly(
                        "1:TEXT_REVISION_REQUIRED:Focus this row in the narration editor, revise the saved script or voice choice, save narration, then regenerate audio/video.",
                        "2:RERENDER_REQUIRED:Regenerate narration audio/video after confirming mix, timing, and media artifacts.",
                        "3:UNREVIEWED:Review playback for this narration segment and save a decision."
                );
        assertThat(resolution.unresolvedSegments().get(0).issueCategories()).containsExactly("TEXT", "VOICE");
    }

    @Test
    void returnsReadyOnlyWhenAllSegmentsAcceptedAndNarratedVideoExists() {
        NarrationPlaybackReviewResolutionVo resolution = service(
                segments().subList(0, 2),
                List.of(
                        record(0, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                        record(1, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), "")
                ),
                List.of(
                        artifact("audio-1", JobArtifactType.NARRATION_AUDIO),
                        artifact("video-1", JobArtifactType.NARRATED_VIDEO)
                )
        ).getResolution("job-resolution");

        assertThat(resolution.status()).isEqualTo("READY");
        assertThat(resolution.nextAction()).isEqualTo("Playback review is resolved and narrated video is ready for demo handoff.");
        assertThat(resolution.readySegmentCount()).isEqualTo(2);
        assertThat(resolution.unresolvedSegmentCount()).isZero();
        assertThat(resolution.unresolvedSegments()).isEmpty();
        assertThat(resolution.videoReady()).isTrue();
    }

    @Test
    void acceptedSegmentsStillRequireNarratedVideoBeforeReady() {
        NarrationPlaybackReviewResolutionVo resolution = service(
                segments().subList(0, 2),
                List.of(
                        record(0, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), ""),
                        record(1, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), "")
                ),
                List.of(artifact("audio-1", JobArtifactType.NARRATION_AUDIO))
        ).getResolution("job-resolution");

        assertThat(resolution.status()).isEqualTo("ATTENTION");
        assertThat(resolution.nextAction()).isEqualTo("Generate narrated video before demo handoff.");
        assertThat(resolution.unresolvedSegmentCount()).isZero();
        assertThat(resolution.videoReady()).isFalse();
    }

    @Test
    void rendersSafeMarkdownWithoutNarrationTextOrReviewerNotes() {
        NarrationPlaybackReviewResolutionService service = service(
                segments(),
                List.of(
                        record(0, NarrationPlaybackReviewDecision.NEEDS_EDIT, List.of(NarrationPlaybackIssueCategory.TEXT), "do not leak note"),
                        record(1, NarrationPlaybackReviewDecision.ACCEPTED, List.of(), "")
                ),
                List.of(artifact("audio-1", JobArtifactType.NARRATION_AUDIO))
        );

        String markdown = service.renderMarkdown("job-resolution");

        assertThat(markdown).contains("# Narration Playback Resolution");
        assertThat(markdown).contains("- Status: ATTENTION");
        assertThat(markdown).contains("- Text revisions required: 1");
        assertThat(markdown).contains("Segment 0");
        assertThat(markdown).doesNotContain("Explain the first scene");
        assertThat(markdown).doesNotContain("do not leak note");
        assertThat(markdown).doesNotContain("/Users/");
        assertThat(markdown).doesNotContain("sk-");
        assertThat(markdown).doesNotContain("objectKey");
    }

    private static NarrationPlaybackReviewResolutionService service(
            List<NarrationSegmentRecord> segments,
            List<NarrationPlaybackReviewRecord> reviews,
            List<JobArtifactVo> artifacts
    ) {
        return new NarrationPlaybackReviewResolutionServiceImpl(
                new StaticNarrationSegmentRepository(segments),
                new StaticPlaybackReviewRepository(reviews),
                new StaticJobArtifactService(artifacts),
                CLOCK
        );
    }

    private static List<NarrationSegmentRecord> segments() {
        Instant now = Instant.parse("2026-06-30T08:00:00Z");
        return List.of(
                segment(0, "15.000", "28.000", "Explain the first scene.", now),
                segment(1, "55.000", "70.000", "Explain the second scene.", now),
                segment(2, "90.000", "105.000", "Explain the third scene.", now),
                segment(3, "130.000", "145.000", "Explain the final scene.", now)
        );
    }

    private static NarrationSegmentRecord segment(int index, String start, String end, String text, Instant now) {
        return new NarrationSegmentRecord(
                "segment-" + index,
                "job-resolution",
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

    private static NarrationPlaybackReviewRecord record(
            int segmentIndex,
            NarrationPlaybackReviewDecision decision,
            List<NarrationPlaybackIssueCategory> categories,
            String note
    ) {
        Instant now = Instant.parse("2026-06-30T08:15:00Z");
        return new NarrationPlaybackReviewRecord(
                "review-" + segmentIndex,
                "job-resolution",
                segmentIndex,
                decision,
                categories,
                note,
                now,
                now
        );
    }

    private static JobArtifactVo artifact(String id, JobArtifactType type) {
        return new JobArtifactVo(
                id,
                "job-resolution",
                type,
                id + ".bin",
                type == JobArtifactType.NARRATED_VIDEO ? "video/mp4" : "audio/mpeg",
                64L,
                id + "-hash",
                false,
                null,
                Instant.parse("2026-06-30T08:20:00Z")
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

    private record StaticPlaybackReviewRepository(List<NarrationPlaybackReviewRecord> reviews)
            implements NarrationPlaybackReviewRepository {
        @Override
        public List<NarrationPlaybackReviewRecord> findByJobId(String jobId) {
            return reviews.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public Optional<NarrationPlaybackReviewRecord> findByJobIdAndSegmentIndex(String jobId, int segmentIndex) {
            return reviews.stream()
                    .filter(record -> record.jobId().equals(jobId) && record.segmentIndex() == segmentIndex)
                    .findFirst();
        }

        @Override
        public NarrationPlaybackReviewRecord upsert(NarrationPlaybackReviewRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByJobId(String jobId) {
            throw new UnsupportedOperationException();
        }
    }

    private record StaticJobArtifactService(List<JobArtifactVo> artifacts) implements JobArtifactService {
        @Override
        public JobArtifactVo createArtifact(CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return artifacts;
        }

        @Override
        public StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            return new StoredObjectResourceBo(artifactId, "application/octet-stream", 0, InputStream.nullInputStream());
        }

        @Override
        public StoredArtifactArchiveBo openArtifactArchive(String jobId) {
            throw new UnsupportedOperationException();
        }
    }
}
