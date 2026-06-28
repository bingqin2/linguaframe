package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitleWorkflowCheckVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitleWorkflowLinkVo;
import com.linguaframe.job.domain.vo.ReviewedSubtitleWorkflowVo;
import com.linguaframe.job.domain.vo.SubtitleDraftSummaryVo;
import com.linguaframe.job.domain.vo.SubtitleReviewSummaryVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.ReviewedSubtitleWorkflowService;
import com.linguaframe.job.service.SubtitleDraftService;
import com.linguaframe.job.service.SubtitleReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class ReviewedSubtitleWorkflowServiceImpl implements ReviewedSubtitleWorkflowService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final Set<JobArtifactType> GENERATED_SUBTITLE_TYPES = EnumSet.of(
            JobArtifactType.TARGET_SUBTITLE_JSON,
            JobArtifactType.TARGET_SUBTITLE_SRT,
            JobArtifactType.TARGET_SUBTITLE_VTT
    );
    private static final Set<JobArtifactType> REVIEWED_SUBTITLE_TYPES = EnumSet.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT
    );

    private final LocalizationJobQueryService queryService;
    private final SubtitleReviewService reviewService;
    private final SubtitleDraftService draftService;
    private final JobArtifactService artifactService;
    private final DeliveryManifestService manifestService;
    private final Clock clock;

    @Autowired
    public ReviewedSubtitleWorkflowServiceImpl(
            LocalizationJobQueryService queryService,
            SubtitleReviewService reviewService,
            SubtitleDraftService draftService,
            JobArtifactService artifactService,
            DeliveryManifestService manifestService
    ) {
        this(queryService, reviewService, draftService, artifactService, manifestService, Clock.systemUTC());
    }

    public ReviewedSubtitleWorkflowServiceImpl(
            LocalizationJobQueryService queryService,
            SubtitleReviewService reviewService,
            SubtitleDraftService draftService,
            JobArtifactService artifactService,
            DeliveryManifestService manifestService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.reviewService = reviewService;
        this.draftService = draftService;
        this.artifactService = artifactService;
        this.manifestService = manifestService;
        this.clock = clock;
    }

    @Override
    public ReviewedSubtitleWorkflowVo workflow(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        String language = job.targetLanguage();
        SubtitleReviewSummaryVo review = safeReview(jobId, language);
        SubtitleDraftSummaryVo draft = safeDraft(jobId, language);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        DeliveryManifestVo manifest = manifestService.buildManifest(jobId);

        int generatedSubtitleArtifactCount = countTypes(artifacts, GENERATED_SUBTITLE_TYPES);
        int reviewedSubtitleArtifactCount = countTypes(artifacts, REVIEWED_SUBTITLE_TYPES);
        boolean reviewedBurnedVideoAvailable = artifacts.stream()
                .anyMatch(artifact -> artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO);
        boolean completed = job.status() == LocalizationJobStatus.COMPLETED;
        boolean reviewIssues = review.missingTargetCount() > 0 || review.timingMismatchCount() > 0;
        boolean reviewedSubtitlesReady = reviewedSubtitleArtifactCount >= REVIEWED_SUBTITLE_TYPES.size();
        boolean handoffReady = manifest.handoffReady();

        List<ReviewedSubtitleWorkflowCheckVo> checks = checks(
                completed,
                review,
                draft,
                generatedSubtitleArtifactCount,
                reviewedSubtitleArtifactCount,
                reviewedBurnedVideoAvailable,
                handoffReady
        );
        String overallStatus = overallStatus(completed, reviewIssues, reviewedSubtitlesReady, handoffReady);
        String phase = phase(completed, reviewIssues, draft.editedSegmentCount(), reviewedSubtitlesReady, handoffReady);

        return new ReviewedSubtitleWorkflowVo(
                job.jobId(),
                job.videoId(),
                language,
                Instant.now(clock),
                overallStatus,
                phase,
                nextAction(phase),
                review.segmentCount(),
                review.missingTargetCount(),
                review.timingMismatchCount(),
                review.qualityScore(),
                review.qualityVerdict(),
                review.qualityIssueCount(),
                review.qualitySuggestedFixCount(),
                draft.editedSegmentCount(),
                draft.lastUpdatedAt(),
                generatedSubtitleArtifactCount,
                reviewedSubtitleArtifactCount,
                reviewedBurnedVideoAvailable,
                handoffReady,
                checks,
                links(job.jobId(), language, reviewedBurnedVideoAvailable),
                safetyNotes()
        );
    }

    private SubtitleReviewSummaryVo safeReview(String jobId, String language) {
        return reviewService.buildReview(jobId, language);
    }

    private SubtitleDraftSummaryVo safeDraft(String jobId, String language) {
        return draftService.getDraft(jobId, language);
    }

    private List<ReviewedSubtitleWorkflowCheckVo> checks(
            boolean completed,
            SubtitleReviewSummaryVo review,
            SubtitleDraftSummaryVo draft,
            int generatedSubtitleArtifactCount,
            int reviewedSubtitleArtifactCount,
            boolean reviewedBurnedVideoAvailable,
            boolean handoffReady
    ) {
        List<ReviewedSubtitleWorkflowCheckVo> checks = new ArrayList<>();
        checks.add(check(
                "JOB_COMPLETED",
                "Job completed",
                completed ? READY : BLOCKED,
                completed ? "The job is completed and can enter subtitle review." : "The job is not completed yet.",
                completed ? "Review generated subtitles." : "Wait for the job to complete before reviewing subtitles.",
                !completed
        ));
        checks.add(check(
                "GENERATED_SUBTITLE_REVIEW",
                "Generated subtitle review",
                review.missingTargetCount() == 0 && review.timingMismatchCount() == 0 ? READY : ATTENTION,
                "Segments: %d, missing targets: %d, timing mismatches: %d.".formatted(
                        review.segmentCount(),
                        review.missingTargetCount(),
                        review.timingMismatchCount()
                ),
                review.missingTargetCount() == 0 && review.timingMismatchCount() == 0
                        ? "Review text quality or publish reviewed subtitles."
                        : "Fix missing or mismatched subtitles in the draft editor.",
                false
        ));
        checks.add(check(
                "DRAFT_EDITS",
                "Draft edits",
                draft.editedSegmentCount() > 0 ? READY : ATTENTION,
                "Edited segments: %d.".formatted(draft.editedSegmentCount()),
                draft.editedSegmentCount() > 0
                        ? "Export or publish the reviewed draft."
                        : "Edit subtitle rows only when generated text needs correction.",
                false
        ));
        checks.add(check(
                "GENERATED_SUBTITLE_ARTIFACTS",
                "Generated subtitle artifacts",
                generatedSubtitleArtifactCount >= GENERATED_SUBTITLE_TYPES.size() ? READY : BLOCKED,
                "Generated subtitle artifacts: %d of %d.".formatted(generatedSubtitleArtifactCount, GENERATED_SUBTITLE_TYPES.size()),
                generatedSubtitleArtifactCount >= GENERATED_SUBTITLE_TYPES.size()
                        ? "Use generated subtitles as draft baseline."
                        : "Wait for generated subtitle JSON/SRT/VTT artifacts.",
                generatedSubtitleArtifactCount < GENERATED_SUBTITLE_TYPES.size()
        ));
        checks.add(check(
                "REVIEWED_SUBTITLE_ARTIFACTS",
                "Reviewed subtitle artifacts",
                reviewedSubtitleArtifactCount >= REVIEWED_SUBTITLE_TYPES.size() ? READY : ATTENTION,
                "Reviewed subtitle artifacts: %d of %d.".formatted(reviewedSubtitleArtifactCount, REVIEWED_SUBTITLE_TYPES.size()),
                reviewedSubtitleArtifactCount >= REVIEWED_SUBTITLE_TYPES.size()
                        ? "Open the delivery manifest or handoff package."
                        : "Publish reviewed subtitles when the draft is ready.",
                false
        ));
        checks.add(check(
                "REVIEWED_BURNED_VIDEO",
                "Reviewed burned video",
                reviewedBurnedVideoAvailable ? READY : ATTENTION,
                reviewedBurnedVideoAvailable ? "Reviewed burned video is available." : "Reviewed burned video is not available.",
                reviewedBurnedVideoAvailable
                        ? "Preview or download the reviewed burned video."
                        : "Include reviewed burned video when publishing if a reviewed preview is needed.",
                false
        ));
        checks.add(check(
                "HANDOFF_READY",
                "Handoff ready",
                handoffReady ? READY : ATTENTION,
                handoffReady ? "Delivery manifest reports handoff ready." : "Delivery manifest still needs reviewed subtitle artifacts.",
                handoffReady ? "Download the handoff package." : "Publish reviewed subtitle artifacts before handoff.",
                false
        ));
        return List.copyOf(checks);
    }

    private ReviewedSubtitleWorkflowCheckVo check(
            String key,
            String label,
            String status,
            String detail,
            String nextAction,
            boolean blocking
    ) {
        return new ReviewedSubtitleWorkflowCheckVo(key, label, status, detail, nextAction, blocking);
    }

    private String overallStatus(boolean completed, boolean reviewIssues, boolean reviewedSubtitlesReady, boolean handoffReady) {
        if (!completed) {
            return BLOCKED;
        }
        if (handoffReady && reviewedSubtitlesReady) {
            return READY;
        }
        return reviewIssues ? ATTENTION : ATTENTION;
    }

    private String phase(
            boolean completed,
            boolean reviewIssues,
            int editedSegmentCount,
            boolean reviewedSubtitlesReady,
            boolean handoffReady
    ) {
        if (!completed) {
            return "WAITING_FOR_JOB";
        }
        if (handoffReady && reviewedSubtitlesReady) {
            return "HANDOFF_READY";
        }
        if (reviewedSubtitlesReady) {
            return "DRAFT_READY";
        }
        if (reviewIssues) {
            return "REVIEW_NEEDED";
        }
        return editedSegmentCount > 0 ? "PUBLISH_READY" : "PUBLISH_READY";
    }

    private String nextAction(String phase) {
        return switch (phase) {
            case "WAITING_FOR_JOB" -> "Wait for the job to complete before reviewing subtitles.";
            case "REVIEW_NEEDED" -> "Fix missing or mismatched subtitles in the draft editor, then publish reviewed subtitles.";
            case "DRAFT_READY" -> "Open the delivery manifest and confirm whether handoff is ready.";
            case "HANDOFF_READY" -> "Download the handoff package or present the reviewed media outputs.";
            case "PUBLISH_READY" -> "Publish reviewed subtitles, optionally including a reviewed burned video.";
            default -> "Open subtitle review and inspect the selected job.";
        };
    }

    private List<ReviewedSubtitleWorkflowLinkVo> links(String jobId, String language, boolean reviewedBurnedVideoAvailable) {
        List<ReviewedSubtitleWorkflowLinkVo> links = new ArrayList<>();
        links.add(link("JOB_DETAIL", "Job detail", "/api/jobs/" + jobId));
        links.add(link("SUBTITLE_REVIEW", "Subtitle review", "/api/jobs/" + jobId + "/subtitle-review?language=" + language));
        links.add(link("SUBTITLE_DRAFT", "Subtitle draft", "/api/jobs/" + jobId + "/subtitle-draft?language=" + language));
        links.add(link("DRAFT_EXPORT_JSON", "Corrected JSON export", "/api/jobs/" + jobId + "/subtitle-draft/export?language=" + language + "&format=json"));
        links.add(link("DRAFT_EXPORT_SRT", "Corrected SRT export", "/api/jobs/" + jobId + "/subtitle-draft/export?language=" + language + "&format=srt"));
        links.add(link("DRAFT_EXPORT_VTT", "Corrected VTT export", "/api/jobs/" + jobId + "/subtitle-draft/export?language=" + language + "&format=vtt"));
        links.add(link("PUBLISH_REVIEWED_SUBTITLES", "Publish reviewed subtitles", "/api/jobs/" + jobId + "/subtitle-draft/publish"));
        links.add(link("DELIVERY_MANIFEST", "Delivery manifest", "/api/jobs/" + jobId + "/delivery-manifest"));
        links.add(link("DELIVERY_MANIFEST_MARKDOWN", "Delivery manifest Markdown", "/api/jobs/" + jobId + "/delivery-manifest/markdown/download"));
        links.add(link("HANDOFF_PACKAGE", "Handoff package", "/api/jobs/" + jobId + "/handoff-package/download"));
        links.add(link("EVIDENCE_MARKDOWN", "Backend evidence", "/api/jobs/" + jobId + "/evidence/markdown/download"));
        if (reviewedBurnedVideoAvailable) {
            links.add(link("REVIEWED_BURNED_VIDEO", "Reviewed burned video", "/api/jobs/" + jobId + "/artifacts"));
        }
        return List.copyOf(links);
    }

    private ReviewedSubtitleWorkflowLinkVo link(String kind, String label, String url) {
        return new ReviewedSubtitleWorkflowLinkVo(kind, label, url);
    }

    private int countTypes(List<JobArtifactVo> artifacts, Set<JobArtifactType> types) {
        return (int) artifacts.stream()
                .map(JobArtifactVo::type)
                .filter(types::contains)
                .count();
    }

    private List<String> safetyNotes() {
        return List.of(
                "Metadata-only workflow: IDs, counts, statuses, timestamps, and safe routes are included.",
                "Transcript text, subtitle text, draft corrections, object keys, local paths, external request bodies, secrets, and media bytes are excluded."
        );
    }
}
