package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.NarrationPlaybackReviewRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationPlaybackReviewDecision;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionLinkVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionSegmentVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.repository.NarrationPlaybackReviewRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarrationPlaybackReviewResolutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NarrationPlaybackReviewResolutionServiceImpl implements NarrationPlaybackReviewResolutionService {

    private final NarrationSegmentRepository segmentRepository;
    private final NarrationPlaybackReviewRepository reviewRepository;
    private final JobArtifactService artifactService;
    private final Clock clock;

    @Autowired
    public NarrationPlaybackReviewResolutionServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationPlaybackReviewRepository reviewRepository,
            JobArtifactService artifactService
    ) {
        this(segmentRepository, reviewRepository, artifactService, Clock.systemUTC());
    }

    public NarrationPlaybackReviewResolutionServiceImpl(
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
    public NarrationPlaybackReviewResolutionVo getResolution(String jobId) {
        List<NarrationSegmentRecord> segments = segmentRepository.findByJobId(jobId);
        List<NarrationPlaybackReviewRecord> reviews = reviewRepository.findByJobId(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        int audioArtifactCount = countArtifacts(artifacts, JobArtifactType.NARRATION_AUDIO);
        int videoArtifactCount = countArtifacts(artifacts, JobArtifactType.NARRATED_VIDEO);

        Map<Integer, NarrationPlaybackReviewRecord> reviewsBySegment = reviews.stream()
                .collect(Collectors.toMap(NarrationPlaybackReviewRecord::segmentIndex, Function.identity(), (left, right) -> right, LinkedHashMap::new));
        List<NarrationPlaybackReviewResolutionSegmentVo> allRows = segments.stream()
                .map(segment -> segmentRow(segment, reviewsBySegment.get(segment.segmentIndex())))
                .toList();
        List<NarrationPlaybackReviewResolutionSegmentVo> unresolvedRows = allRows.stream()
                .filter(row -> !row.resolutionStatus().equals("READY"))
                .toList();

        int readyCount = Math.toIntExact(allRows.stream().filter(row -> row.resolutionStatus().equals("READY")).count());
        int textRevisionCount = countStatus(allRows, "TEXT_REVISION_REQUIRED");
        int rerenderCount = countStatus(allRows, "RERENDER_REQUIRED");
        int unreviewedCount = countStatus(allRows, "UNREVIEWED");
        String status = status(segments.size(), unresolvedRows.size(), videoArtifactCount);

        return new NarrationPlaybackReviewResolutionVo(
                jobId,
                Instant.now(clock),
                status,
                nextAction(status, unresolvedRows.size(), videoArtifactCount),
                segments.size(),
                readyCount,
                unresolvedRows.size(),
                textRevisionCount,
                rerenderCount,
                unreviewedCount,
                audioArtifactCount > 0,
                audioArtifactCount,
                videoArtifactCount > 0,
                videoArtifactCount,
                unresolvedRows,
                links(jobId),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationPlaybackReviewResolutionVo resolution = getResolution(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# Narration Playback Resolution");
        lines.add("");
        lines.add("- Job: " + resolution.jobId());
        lines.add("- Status: " + resolution.status());
        lines.add("- Next action: " + resolution.nextAction());
        lines.add("- Segments: " + resolution.segmentCount());
        lines.add("- Ready segments: " + resolution.readySegmentCount());
        lines.add("- Unresolved segments: " + resolution.unresolvedSegmentCount());
        lines.add("- Text revisions required: " + resolution.textRevisionRequiredCount());
        lines.add("- Rerenders required: " + resolution.rerenderRequiredCount());
        lines.add("- Unreviewed: " + resolution.unreviewedSegmentCount());
        lines.add("- Narration audio ready: " + resolution.audioReady());
        lines.add("- Narrated video ready: " + resolution.videoReady());
        lines.add("");
        lines.add("## Unresolved Segments");
        if (resolution.unresolvedSegments().isEmpty()) {
            lines.add("- No unresolved narration playback rows.");
        } else {
            for (NarrationPlaybackReviewResolutionSegmentVo segment : resolution.unresolvedSegments()) {
                lines.add("- Segment " + segment.segmentIndex()
                        + ": " + segment.startSeconds() + "s-" + segment.endSeconds() + "s"
                        + ", decision=" + segment.decision()
                        + ", status=" + segment.resolutionStatus()
                        + ", issues=" + (segment.issueCategories().isEmpty() ? "none" : String.join(",", segment.issueCategories()))
                        + ", reviewerNotePresent=" + segment.reviewerNotePresent()
                        + ", nextAction=" + segment.nextAction());
            }
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationPlaybackReviewResolutionLinkVo link : resolution.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : resolution.safetyNotes()) {
            lines.add("- " + note);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    private NarrationPlaybackReviewResolutionSegmentVo segmentRow(
            NarrationSegmentRecord segment,
            NarrationPlaybackReviewRecord review
    ) {
        NarrationPlaybackReviewDecision decision = review == null
                ? NarrationPlaybackReviewDecision.UNREVIEWED
                : review.decision();
        String resolutionStatus = resolutionStatus(decision);
        return new NarrationPlaybackReviewResolutionSegmentVo(
                segment.segmentIndex(),
                segment.startSeconds(),
                segment.endSeconds(),
                segment.endSeconds().subtract(segment.startSeconds()),
                decision.name(),
                resolutionStatus,
                review == null ? List.of() : review.issueCategories().stream().map(Enum::name).toList(),
                segmentNextAction(resolutionStatus),
                review != null && review.reviewerNote() != null && !review.reviewerNote().isBlank(),
                review == null ? null : review.updatedAt()
        );
    }

    private String resolutionStatus(NarrationPlaybackReviewDecision decision) {
        return switch (decision) {
            case ACCEPTED -> "READY";
            case NEEDS_EDIT -> "TEXT_REVISION_REQUIRED";
            case NEEDS_RERENDER -> "RERENDER_REQUIRED";
            case UNREVIEWED -> "UNREVIEWED";
        };
    }

    private String segmentNextAction(String resolutionStatus) {
        return switch (resolutionStatus) {
            case "READY" -> "No action required for this segment.";
            case "TEXT_REVISION_REQUIRED" -> "Focus this row in the narration editor, revise the saved script or voice choice, save narration, then regenerate audio/video.";
            case "RERENDER_REQUIRED" -> "Regenerate narration audio/video after confirming mix, timing, and media artifacts.";
            default -> "Review playback for this narration segment and save a decision.";
        };
    }

    private String status(int segmentCount, int unresolvedCount, int videoArtifactCount) {
        if (segmentCount <= 0) {
            return "BLOCKED";
        }
        return unresolvedCount == 0 && videoArtifactCount > 0 ? "READY" : "ATTENTION";
    }

    private String nextAction(String status, int unresolvedCount, int videoArtifactCount) {
        if (status.equals("BLOCKED")) {
            return "Save narration segments before resolving playback review.";
        }
        if (unresolvedCount > 0) {
            return "Resolve playback review issues, save narration edits, and regenerate narration media before handoff.";
        }
        if (videoArtifactCount <= 0) {
            return "Generate narrated video before demo handoff.";
        }
        return "Playback review is resolved and narrated video is ready for demo handoff.";
    }

    private int countStatus(List<NarrationPlaybackReviewResolutionSegmentVo> rows, String status) {
        return Math.toIntExact(rows.stream().filter(row -> row.resolutionStatus().equals(status)).count());
    }

    private int countArtifacts(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return Math.toIntExact(artifacts.stream().filter(artifact -> artifact.type() == type).count());
    }

    private List<NarrationPlaybackReviewResolutionLinkVo> links(String jobId) {
        return List.of(
                new NarrationPlaybackReviewResolutionLinkVo(
                        "NARRATION_PLAYBACK_RESOLUTION",
                        "Narration playback resolution JSON",
                        "/api/jobs/" + jobId + "/narration-playback-review/resolution",
                        "application/json"
                ),
                new NarrationPlaybackReviewResolutionLinkVo(
                        "NARRATION_PLAYBACK_RESOLUTION_MARKDOWN",
                        "Narration playback resolution Markdown",
                        "/api/jobs/" + jobId + "/narration-playback-review/resolution/markdown/download",
                        "text/markdown"
                )
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration playback resolution is metadata-only.",
                "Resolution evidence excludes narration text, reviewer note bodies, provider payloads, object keys, local paths, tokens, API keys, credentials, and media bytes.",
                "Focusing or editing rows remains a browser-local draft action until the operator saves narration and explicitly regenerates audio/video."
        );
    }
}
