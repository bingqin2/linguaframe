package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.dto.UpdateNarrationPlaybackReviewSegmentDto;
import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationPlaybackIssueCategory;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewCategoryCountVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewLinkVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewSegmentVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;
import com.linguaframe.job.repository.NarrationPlaybackReviewRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarrationPlaybackReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NarrationPlaybackReviewServiceImpl implements NarrationPlaybackReviewService {

    private static final int REVIEWER_NOTE_MAX_LENGTH = 1000;

    private final NarrationSegmentRepository segmentRepository;
    private final NarrationPlaybackReviewRepository reviewRepository;
    private final JobArtifactService artifactService;
    private final Clock clock;

    @Autowired
    public NarrationPlaybackReviewServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationPlaybackReviewRepository reviewRepository,
            JobArtifactService artifactService
    ) {
        this(segmentRepository, reviewRepository, artifactService, Clock.systemUTC());
    }

    public NarrationPlaybackReviewServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationPlaybackReviewRepository reviewRepository,
            JobArtifactService artifactService,
            Clock clock
    ) {
        this.segmentRepository = segmentRepository;
        this.reviewRepository = reviewRepository;
        this.artifactService = artifactService;
        this.clock = clock;
    }

    @Override
    public NarrationPlaybackReviewVo getReview(String jobId) {
        List<NarrationSegmentRecord> segments = segmentRepository.findByJobId(jobId);
        List<NarrationPlaybackReviewRecord> reviewRecords = reviewRepository.findByJobId(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        int audioArtifactCount = countArtifacts(artifacts, JobArtifactType.NARRATION_AUDIO);
        int videoArtifactCount = countArtifacts(artifacts, JobArtifactType.NARRATED_VIDEO);

        Map<Integer, NarrationPlaybackReviewRecord> recordsBySegment = reviewRecords.stream()
                .collect(Collectors.toMap(NarrationPlaybackReviewRecord::segmentIndex, Function.identity(), (left, right) -> right, LinkedHashMap::new));
        List<NarrationPlaybackReviewSegmentVo> segmentRows = segments.stream()
                .map(segment -> segmentRow(segment, recordsBySegment.get(segment.segmentIndex())))
                .toList();

        int segmentCount = segments.size();
        int acceptedCount = count(segmentRows, NarrationPlaybackReviewDecision.ACCEPTED);
        int needsEditCount = count(segmentRows, NarrationPlaybackReviewDecision.NEEDS_EDIT);
        int needsRerenderCount = count(segmentRows, NarrationPlaybackReviewDecision.NEEDS_RERENDER);
        int unreviewedCount = count(segmentRows, NarrationPlaybackReviewDecision.UNREVIEWED);
        int reviewedCount = segmentCount - unreviewedCount;
        String status = status(segmentCount, acceptedCount);

        return new NarrationPlaybackReviewVo(
                jobId,
                Instant.now(clock),
                status,
                nextAction(status),
                segmentCount,
                reviewedCount,
                acceptedCount,
                needsEditCount,
                needsRerenderCount,
                unreviewedCount,
                audioArtifactCount > 0,
                audioArtifactCount,
                videoArtifactCount > 0,
                videoArtifactCount,
                decisionCounts(segmentRows),
                issueCategoryCounts(segmentRows),
                segmentRows,
                links(jobId),
                safetyNotes()
        );
    }

    @Override
    public NarrationPlaybackReviewVo updateSegmentReview(String jobId, int segmentIndex, UpdateNarrationPlaybackReviewSegmentDto request) {
        boolean segmentExists = segmentRepository.findByJobId(jobId).stream()
                .anyMatch(segment -> segment.segmentIndex() == segmentIndex);
        if (!segmentExists) {
            throw new NoSuchElementException("Narration segment not found for playback review.");
        }
        Instant now = Instant.now(clock);
        NarrationPlaybackReviewRecord existing = reviewRepository.findByJobIdAndSegmentIndex(jobId, segmentIndex).orElse(null);
        reviewRepository.upsert(new NarrationPlaybackReviewRecord(
                existing == null ? UUID.randomUUID().toString() : existing.id(),
                jobId,
                segmentIndex,
                request.decision() == null ? NarrationPlaybackReviewDecision.UNREVIEWED : request.decision(),
                sanitizeCategories(request.issueCategories()),
                normalizeNote(request.reviewerNote()),
                existing == null ? now : existing.createdAt(),
                now
        ));
        return getReview(jobId);
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationPlaybackReviewVo review = getReview(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# Narration Playback Review");
        lines.add("");
        lines.add("- Job: " + review.jobId());
        lines.add("- Status: " + review.status());
        lines.add("- Next action: " + review.nextAction());
        lines.add("- Segments: " + review.segmentCount());
        lines.add("- Reviewed: " + review.reviewedSegmentCount());
        lines.add("- Accepted: " + review.acceptedSegmentCount());
        lines.add("- Needs edit: " + review.needsEditCount());
        lines.add("- Needs rerender: " + review.needsRerenderCount());
        lines.add("- Unreviewed: " + review.unreviewedSegmentCount());
        lines.add("- Narration audio ready: " + review.audioReady());
        lines.add("- Narrated video ready: " + review.videoReady());
        lines.add("");
        lines.add("## Segments");
        if (review.segments().isEmpty()) {
            lines.add("- No narration segments saved.");
        } else {
            for (NarrationPlaybackReviewSegmentVo segment : review.segments()) {
                lines.add("- Segment " + segment.segmentIndex()
                        + ": " + segment.startSeconds() + "s-" + segment.endSeconds() + "s"
                        + ", decision=" + segment.decision()
                        + ", issues=" + (segment.issueCategories().isEmpty() ? "none" : String.join(",", segment.issueCategories()))
                        + ", reviewerNotePresent=" + segment.reviewerNotePresent());
            }
        }
        lines.add("");
        lines.add("## Decision Counts");
        for (NarrationPlaybackReviewCategoryCountVo count : review.decisionCounts()) {
            lines.add("- " + count.category() + ": " + count.count());
        }
        lines.add("");
        lines.add("## Issue Category Counts");
        for (NarrationPlaybackReviewCategoryCountVo count : review.issueCategoryCounts()) {
            lines.add("- " + count.category() + ": " + count.count());
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationPlaybackReviewLinkVo link : review.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : review.safetyNotes()) {
            lines.add("- " + note);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    private NarrationPlaybackReviewSegmentVo segmentRow(
            NarrationSegmentRecord segment,
            NarrationPlaybackReviewRecord review
    ) {
        NarrationPlaybackReviewDecision decision = review == null ? NarrationPlaybackReviewDecision.UNREVIEWED : review.decision();
        List<String> categories = review == null
                ? List.of()
                : review.issueCategories().stream().map(Enum::name).toList();
        return new NarrationPlaybackReviewSegmentVo(
                segment.segmentIndex(),
                segment.startSeconds(),
                segment.endSeconds(),
                segment.endSeconds().subtract(segment.startSeconds()),
                decision.name(),
                categories,
                review != null && !blank(review.reviewerNote()),
                review == null ? null : review.updatedAt()
        );
    }

    private int count(List<NarrationPlaybackReviewSegmentVo> segments, NarrationPlaybackReviewDecision decision) {
        return Math.toIntExact(segments.stream().filter(segment -> segment.decision().equals(decision.name())).count());
    }

    private String status(int segmentCount, int acceptedCount) {
        if (segmentCount <= 0) {
            return "BLOCKED";
        }
        return acceptedCount == segmentCount ? "READY" : "ATTENTION";
    }

    private String nextAction(String status) {
        return switch (status) {
            case "READY" -> "Playback review is ready for narration handoff.";
            case "BLOCKED" -> "Save narration segments before playback review.";
            default -> "Review or revise narration segments before handoff.";
        };
    }

    private List<NarrationPlaybackReviewCategoryCountVo> decisionCounts(List<NarrationPlaybackReviewSegmentVo> segments) {
        Map<NarrationPlaybackReviewDecision, Integer> counts = new EnumMap<>(NarrationPlaybackReviewDecision.class);
        for (NarrationPlaybackReviewDecision decision : NarrationPlaybackReviewDecision.values()) {
            counts.put(decision, 0);
        }
        for (NarrationPlaybackReviewSegmentVo segment : segments) {
            counts.merge(NarrationPlaybackReviewDecision.valueOf(segment.decision()), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new NarrationPlaybackReviewCategoryCountVo(entry.getKey().name(), entry.getValue()))
                .toList();
    }

    private List<NarrationPlaybackReviewCategoryCountVo> issueCategoryCounts(List<NarrationPlaybackReviewSegmentVo> segments) {
        Map<NarrationPlaybackIssueCategory, Integer> counts = new EnumMap<>(NarrationPlaybackIssueCategory.class);
        for (NarrationPlaybackIssueCategory category : NarrationPlaybackIssueCategory.values()) {
            counts.put(category, 0);
        }
        for (NarrationPlaybackReviewSegmentVo segment : segments) {
            for (String category : segment.issueCategories()) {
                counts.merge(NarrationPlaybackIssueCategory.valueOf(category), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new NarrationPlaybackReviewCategoryCountVo(entry.getKey().name(), entry.getValue()))
                .toList();
    }

    private int countArtifacts(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return Math.toIntExact(artifacts.stream().filter(artifact -> artifact.type() == type).count());
    }

    private List<NarrationPlaybackIssueCategory> sanitizeCategories(List<NarrationPlaybackIssueCategory> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return categories.stream().distinct().toList();
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return "";
        }
        String normalized = note.trim();
        return normalized.length() <= REVIEWER_NOTE_MAX_LENGTH
                ? normalized
                : normalized.substring(0, REVIEWER_NOTE_MAX_LENGTH);
    }

    private List<NarrationPlaybackReviewLinkVo> links(String jobId) {
        return List.of(
                link("NARRATION_WORKSPACE", "Narration workspace", "/api/jobs/" + jobId + "/narration-workspace"),
                link("NARRATION_PLAYBACK_REVIEW", "Narration playback review", "/api/jobs/" + jobId + "/narration-playback-review"),
                link("NARRATION_PLAYBACK_REVIEW_MARKDOWN", "Narration playback review Markdown", "/api/jobs/" + jobId + "/narration-playback-review/markdown/download"),
                link("NARRATION_RENDER_REVIEW", "Narration render review", "/api/jobs/" + jobId + "/narration-render-review"),
                link("NARRATION_EVIDENCE", "Narration evidence", "/api/jobs/" + jobId + "/narration-evidence")
        );
    }

    private NarrationPlaybackReviewLinkVo link(String key, String label, String href) {
        return new NarrationPlaybackReviewLinkVo(key, label, href);
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration playback review is metadata-only and excludes narration text bodies, transcript text, subtitle text, reviewer note bodies, object keys, local paths, provider payloads, tokens, API keys, and media bytes.",
                "Reviewer notes remain available only in the interactive playback review editor.",
                "This review does not call OpenAI, TTS providers, FFmpeg, or mutate generated media artifacts."
        );
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
