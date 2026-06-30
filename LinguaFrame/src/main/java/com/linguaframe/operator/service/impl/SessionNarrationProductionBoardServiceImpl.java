package com.linguaframe.operator.service.impl;

import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.LocalizationJobSummaryVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.repository.LocalizationJobRepository;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.NarrationDeliveryPackageService;
import com.linguaframe.job.service.NarrationPlaybackReviewResolutionService;
import com.linguaframe.job.service.NarrationRenderReviewService;
import com.linguaframe.job.service.NarrationSceneBoardService;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionActionVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionBoardVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionCheckVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionJobVo;
import com.linguaframe.operator.domain.vo.SessionNarrationProductionLinkVo;
import com.linguaframe.operator.service.SessionNarrationProductionBoardService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SessionNarrationProductionBoardServiceImpl implements SessionNarrationProductionBoardService {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 50;
    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";
    private static final String EMPTY = "EMPTY";

    private final LocalizationJobRepository jobRepository;
    private final NarrationSceneBoardService sceneBoardService;
    private final NarrationRenderReviewService renderReviewService;
    private final NarrationPlaybackReviewResolutionService playbackResolutionService;
    private final NarrationDeliveryPackageService deliveryPackageService;
    private final DemoAcceptanceGateService acceptanceGateService;

    public SessionNarrationProductionBoardServiceImpl(
            LocalizationJobRepository jobRepository,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            NarrationDeliveryPackageService deliveryPackageService,
            DemoAcceptanceGateService acceptanceGateService
    ) {
        this.jobRepository = jobRepository;
        this.sceneBoardService = sceneBoardService;
        this.renderReviewService = renderReviewService;
        this.playbackResolutionService = playbackResolutionService;
        this.deliveryPackageService = deliveryPackageService;
        this.acceptanceGateService = acceptanceGateService;
    }

    @Override
    public SessionNarrationProductionBoardVo board(Integer limit) {
        int resolvedLimit = resolvedLimit(limit);
        List<SessionNarrationProductionJobVo> jobs = jobRepository.findSummaries(null, resolvedLimit, 0).stream()
                .map(this::job)
                .sorted(Comparator.comparingInt((SessionNarrationProductionJobVo item) -> classificationRank(item.classification()))
                        .thenComparing(SessionNarrationProductionJobVo::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int ready = count(jobs, "READY_TO_DELIVER");
        int needsReview = count(jobs, "NEEDS_REVIEW");
        int needsRender = count(jobs, "NEEDS_RENDER");
        int needsAuthoring = count(jobs, "NEEDS_AUTHORING");
        int blocked = count(jobs, BLOCKED);
        int notApplicable = count(jobs, "NOT_APPLICABLE");
        String overall = blocked > 0 ? BLOCKED : needsReview > 0 || needsRender > 0 || needsAuthoring > 0 ? ATTENTION : jobs.isEmpty() ? EMPTY : READY;
        SessionNarrationProductionActionVo primaryAction = jobs.stream()
                .flatMap(item -> item.actions().stream())
                .filter(SessionNarrationProductionActionVo::primary)
                .findFirst()
                .orElse(null);
        String headline = headline(overall, ready, needsReview, needsRender, needsAuthoring, blocked);
        String nextAction = primaryAction == null ? defaultNextAction(overall) : primaryAction.detail();
        List<SessionNarrationProductionCheckVo> checks = boardChecks(ready, needsReview, needsRender, needsAuthoring, blocked, notApplicable);
        List<SessionNarrationProductionLinkVo> links = boardLinks();
        List<String> safetyNotes = safetyNotes();
        String markdown = markdown(overall, headline, nextAction, resolvedLimit, ready, needsReview, needsRender, needsAuthoring, blocked, notApplicable, jobs, checks, links, safetyNotes);
        return new SessionNarrationProductionBoardVo(
                Instant.now(),
                overall,
                headline,
                nextAction,
                resolvedLimit,
                ready,
                needsReview,
                needsRender,
                needsAuthoring,
                blocked,
                notApplicable,
                primaryAction,
                jobs,
                checks,
                links,
                safetyNotes,
                markdown
        );
    }

    @Override
    public String boardMarkdown(Integer limit) {
        return board(limit).markdown();
    }

    private SessionNarrationProductionJobVo job(LocalizationJobSummaryVo summary) {
        if (summary.status() != LocalizationJobStatus.COMPLETED) {
            return notApplicable(summary);
        }
        try {
            NarrationSceneBoardVo sceneBoard = sceneBoardService.getSceneBoard(summary.jobId());
            NarrationRenderReviewVo renderReview = safeRenderReview(summary.jobId());
            NarrationPlaybackReviewResolutionVo resolution = safeResolution(summary.jobId());
            NarrationDeliveryPackageVo delivery = safeDelivery(summary.jobId());
            DemoAcceptanceGateVo acceptance = safeAcceptance(summary.jobId());
            return completedJob(summary, sceneBoard, renderReview, resolution, delivery, acceptance);
        } catch (RuntimeException ex) {
            return blockedByFailure(summary);
        }
    }

    private SessionNarrationProductionJobVo completedJob(
            LocalizationJobSummaryVo summary,
            NarrationSceneBoardVo sceneBoard,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewResolutionVo resolution,
            NarrationDeliveryPackageVo delivery,
            DemoAcceptanceGateVo acceptance
    ) {
        int segmentCount = sceneBoard == null ? 0 : sceneBoard.segmentCount();
        boolean sceneReady = sceneBoard != null && READY.equals(sceneBoard.status());
        boolean sceneBlocked = sceneBoard != null && BLOCKED.equals(sceneBoard.status());
        boolean audioReady = (sceneBoard != null && sceneBoard.audioReady())
                || (renderReview != null && renderReview.audioReady())
                || (resolution != null && resolution.audioReady())
                || (delivery != null && delivery.audioReady());
        boolean videoReady = (sceneBoard != null && sceneBoard.videoReady())
                || (renderReview != null && renderReview.videoReady())
                || (resolution != null && resolution.videoReady())
                || (delivery != null && delivery.videoReady());
        boolean renderReady = renderReview != null && READY.equals(renderReview.status());
        boolean playbackResolved = resolution != null && READY.equals(resolution.status()) && resolution.unresolvedSegmentCount() == 0;
        boolean deliveryReady = delivery != null && READY.equals(delivery.status());
        boolean acceptanceReady = acceptance != null && READY.equals(acceptance.gateStatus());
        String classification;
        String blocker;
        String nextAction;
        if (sceneBlocked) {
            classification = BLOCKED;
            blocker = "Scene board is blocked by timing, voice, or saved narration evidence.";
            nextAction = "Open the narration scene board and resolve blocked checks.";
        } else if (segmentCount == 0) {
            classification = "NEEDS_AUTHORING";
            blocker = "No saved narration rows exist for this completed job.";
            nextAction = "Open the narration workspace and add timed narration rows.";
        } else if (!audioReady || !videoReady || !renderReady || delivery == null) {
            classification = "NEEDS_RENDER";
            blocker = "Narration render or generated media evidence is missing.";
            nextAction = "Open render review, render narration audio/video, then refresh delivery.";
        } else if (!playbackResolved || !deliveryReady || !acceptanceReady) {
            classification = "NEEDS_REVIEW";
            blocker = "Narration playback, delivery, or final acceptance still needs review.";
            nextAction = "Open playback resolution and acceptance gate before presenting.";
        } else {
            classification = "READY_TO_DELIVER";
            blocker = "Narration delivery evidence is ready.";
            nextAction = "Download narration delivery package or continue final handoff.";
        }
        return row(
                summary,
                classification,
                attention(classification),
                segmentCount,
                sceneBoard == null ? BigDecimal.ZERO : sceneBoard.coveragePercent(),
                sceneBoard == null ? 0 : sceneBoard.gapCount(),
                sceneBoard != null && sceneBoard.hasOverlap(),
                sceneBoard == null ? 0 : sceneBoard.voiceCount(),
                sceneBoard == null ? 0 : sceneBoard.mixKeyframeCount(),
                sceneReady,
                audioReady,
                videoReady,
                renderReady,
                playbackResolved,
                deliveryReady,
                acceptanceReady,
                blocker,
                nextAction,
                checks(classification, sceneReady, audioReady, videoReady, renderReady, playbackResolved, deliveryReady, acceptanceReady),
                actions(summary.jobId(), classification),
                links(summary.jobId(), classification)
        );
    }

    private SessionNarrationProductionJobVo notApplicable(LocalizationJobSummaryVo summary) {
        return row(
                summary,
                "NOT_APPLICABLE",
                ATTENTION,
                0,
                BigDecimal.ZERO,
                0,
                false,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                "Job is not completed, so narration production cannot start yet.",
                "Wait for localization completion, then open the narration workspace.",
                checks("NOT_APPLICABLE", false, false, false, false, false, false, false),
                actions(summary.jobId(), "NOT_APPLICABLE"),
                links(summary.jobId(), "NOT_APPLICABLE")
        );
    }

    private SessionNarrationProductionJobVo blockedByFailure(LocalizationJobSummaryVo summary) {
        return row(
                summary,
                BLOCKED,
                BLOCKED,
                0,
                BigDecimal.ZERO,
                0,
                false,
                0,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                "Narration production evidence could not be loaded safely.",
                "Open the job detail and per-job narration surfaces to inspect safe evidence.",
                checks(BLOCKED, false, false, false, false, false, false, false),
                actions(summary.jobId(), BLOCKED),
                links(summary.jobId(), BLOCKED)
        );
    }

    private SessionNarrationProductionJobVo row(
            LocalizationJobSummaryVo summary,
            String classification,
            String attentionLevel,
            int segmentCount,
            BigDecimal coveragePercent,
            int gapCount,
            boolean hasOverlap,
            int voiceCount,
            int mixKeyframeCount,
            boolean sceneBoardReady,
            boolean audioReady,
            boolean videoReady,
            boolean renderReviewReady,
            boolean playbackResolved,
            boolean deliveryReady,
            boolean acceptanceReady,
            String primaryBlocker,
            String nextAction,
            List<SessionNarrationProductionCheckVo> checks,
            List<SessionNarrationProductionActionVo> actions,
            List<SessionNarrationProductionLinkVo> links
    ) {
        return new SessionNarrationProductionJobVo(
                summary.jobId(),
                summary.videoId(),
                summary.status().name(),
                classification,
                attentionLevel,
                summary.targetLanguage(),
                summary.createdAt(),
                summary.completedAt(),
                segmentCount,
                coveragePercent,
                gapCount,
                hasOverlap,
                voiceCount,
                mixKeyframeCount,
                sceneBoardReady,
                audioReady,
                videoReady,
                renderReviewReady,
                playbackResolved,
                deliveryReady,
                acceptanceReady,
                primaryBlocker,
                nextAction,
                checks,
                actions,
                links
        );
    }

    private NarrationRenderReviewVo safeRenderReview(String jobId) {
        try {
            return renderReviewService.getReview(jobId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private NarrationPlaybackReviewResolutionVo safeResolution(String jobId) {
        try {
            return playbackResolutionService.getResolution(jobId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private NarrationDeliveryPackageVo safeDelivery(String jobId) {
        try {
            return deliveryPackageService.getSummary(jobId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private DemoAcceptanceGateVo safeAcceptance(String jobId) {
        try {
            return acceptanceGateService.buildGate(jobId);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private List<SessionNarrationProductionCheckVo> checks(
            String classification,
            boolean sceneBoardReady,
            boolean audioReady,
            boolean videoReady,
            boolean renderReviewReady,
            boolean playbackResolved,
            boolean deliveryReady,
            boolean acceptanceReady
    ) {
        return List.of(
                check("scene-board", "Scene board", sceneBoardReady, "Saved narration scene-board metadata.", "Open the scene board.", BLOCKED.equals(classification)),
                check("audio", "Narration audio", audioReady, "Narration audio readiness.", "Render narration audio.", "NEEDS_RENDER".equals(classification)),
                check("video", "Narrated video", videoReady, "Narrated video readiness.", "Render narrated video.", "NEEDS_RENDER".equals(classification)),
                check("render-review", "Render review", renderReviewReady, "Render review readiness.", "Open render review.", "NEEDS_RENDER".equals(classification)),
                check("playback-resolution", "Playback resolved", playbackResolved, "Playback review resolution readiness.", "Open playback resolution.", "NEEDS_REVIEW".equals(classification)),
                check("delivery", "Delivery package", deliveryReady, "Narration delivery package readiness.", "Download or refresh delivery package.", "NEEDS_REVIEW".equals(classification)),
                check("acceptance", "Acceptance gate", acceptanceReady, "Final acceptance readiness.", "Open acceptance gate.", "NEEDS_REVIEW".equals(classification))
        );
    }

    private SessionNarrationProductionCheckVo check(String key, String label, boolean ready, String detail, String nextAction, boolean blocking) {
        return new SessionNarrationProductionCheckVo(key, label, ready ? READY : ATTENTION, detail, nextAction, blocking && !ready);
    }

    private List<SessionNarrationProductionActionVo> actions(String jobId, String classification) {
        List<SessionNarrationProductionActionVo> actions = new ArrayList<>();
        if (BLOCKED.equals(classification)) {
            actions.add(action("OPEN_SCENE_BOARD", "Open scene board", "/api/jobs/" + jobId + "/narration-scene-board", "Open scene board and inspect blocked checks.", true));
            actions.add(action("OPEN_JOB", "Open job detail", "/api/jobs/" + jobId, "Inspect safe job detail.", false));
        } else if ("NEEDS_AUTHORING".equals(classification)) {
            actions.add(action("OPEN_NARRATION_WORKSPACE", "Open narration workspace", "/api/jobs/" + jobId, "Add timed narration rows in the browser workspace.", true));
        } else if ("NEEDS_RENDER".equals(classification)) {
            actions.add(action("OPEN_RENDER_REVIEW", "Open render review", "/api/jobs/" + jobId + "/narration-render-review", "Review render readiness and generate missing media through explicit actions.", true));
            actions.add(action("OPEN_SCENE_BOARD", "Open scene board", "/api/jobs/" + jobId + "/narration-scene-board", "Inspect saved narration windows.", false));
        } else if ("NEEDS_REVIEW".equals(classification)) {
            actions.add(action("OPEN_PLAYBACK_RESOLUTION", "Open playback resolution", "/api/jobs/" + jobId + "/narration-playback-review-resolution", "Resolve playback review rows.", true));
            actions.add(action("OPEN_ACCEPTANCE_GATE", "Open acceptance gate", "/api/jobs/" + jobId + "/demo-acceptance-gate", "Verify final demo readiness.", false));
        } else if ("READY_TO_DELIVER".equals(classification)) {
            actions.add(action("OPEN_DELIVERY_PACKAGE", "Open narration delivery", "/api/jobs/" + jobId + "/narration-delivery-package", "Download narration delivery package.", true));
        } else {
            actions.add(action("OPEN_RUN_MONITOR", "Open run monitor", "/api/jobs/" + jobId + "/demo-run-monitor", "Wait for job completion before narration production.", true));
        }
        return List.copyOf(actions);
    }

    private SessionNarrationProductionActionVo action(String key, String label, String href, String detail, boolean primary) {
        return new SessionNarrationProductionActionVo(key, label, href, detail, primary);
    }

    private List<SessionNarrationProductionLinkVo> links(String jobId, String classification) {
        List<SessionNarrationProductionLinkVo> links = new ArrayList<>();
        links.add(link("JOB", "Job detail", "/api/jobs/" + jobId, "application/json", "Safe job detail."));
        links.add(link("SCENE_BOARD", "Narration scene board", "/api/jobs/" + jobId + "/narration-scene-board", "application/json", "Saved narration scene-board metadata."));
        links.add(link("RENDER_REVIEW", "Render review", "/api/jobs/" + jobId + "/narration-render-review", "application/json", "Narration render review."));
        links.add(link("PLAYBACK_RESOLUTION", "Playback resolution", "/api/jobs/" + jobId + "/narration-playback-review-resolution", "application/json", "Playback resolution gate."));
        links.add(link("DELIVERY_PACKAGE", "Narration delivery", "/api/jobs/" + jobId + "/narration-delivery-package", "application/json", "Narration delivery package."));
        links.add(link("ACCEPTANCE_GATE", "Acceptance gate", "/api/jobs/" + jobId + "/demo-acceptance-gate", "application/json", "Final demo gate."));
        if (BLOCKED.equals(classification) || "NOT_APPLICABLE".equals(classification)) {
            links.add(link("STUCK_RECOVERY", "Stuck job recovery", "/api/jobs/" + jobId + "/stuck-job-recovery", "application/json", "Per-job recovery cockpit."));
        }
        return List.copyOf(links);
    }

    private SessionNarrationProductionLinkVo link(String key, String label, String href, String contentType, String description) {
        return new SessionNarrationProductionLinkVo(key, label, href, contentType, description);
    }

    private List<SessionNarrationProductionCheckVo> boardChecks(
            int ready,
            int needsReview,
            int needsRender,
            int needsAuthoring,
            int blocked,
            int notApplicable
    ) {
        return List.of(
                new SessionNarrationProductionCheckVo("blocked", "Blocked", blocked > 0 ? BLOCKED : READY, blocked + " job(s) have blocked narration production evidence.", "Open the first blocked narration row.", blocked > 0),
                new SessionNarrationProductionCheckVo("needs-review", "Needs review", needsReview > 0 ? ATTENTION : READY, needsReview + " job(s) need playback or acceptance review.", "Open playback resolution rows.", false),
                new SessionNarrationProductionCheckVo("needs-render", "Needs render", needsRender > 0 ? ATTENTION : READY, needsRender + " job(s) need narration audio/video or render evidence.", "Open render review rows.", false),
                new SessionNarrationProductionCheckVo("needs-authoring", "Needs authoring", needsAuthoring > 0 ? ATTENTION : READY, needsAuthoring + " completed job(s) have no saved narration rows.", "Open narration workspace.", false),
                new SessionNarrationProductionCheckVo("ready", "Ready", READY, ready + " job(s) are ready for narration delivery.", "Download delivery package.", false),
                new SessionNarrationProductionCheckVo("not-applicable", "Not applicable", READY, notApplicable + " job(s) cannot be narrated yet.", "Wait for completion.", false)
        );
    }

    private List<SessionNarrationProductionLinkVo> boardLinks() {
        return List.of(
                link("COMMAND_CENTER", "Demo session command center", "/api/operator/demo-session-command-center", "application/json", "Run-day command center."),
                link("RECOVERY_BOARD", "Demo session recovery board", "/api/operator/demo-session-recovery-board", "application/json", "Run-day recovery board."),
                link("MARKDOWN", "Session narration production Markdown", "/api/operator/session-narration-production-board/markdown/download", "text/markdown", "Downloadable narration production report.")
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Session narration production board is metadata-only and read-only.",
                "It does not call OpenAI, TTS providers, FFmpeg, upload APIs, recovery actions, retry/cancel APIs, object-storage writes, or database mutation paths.",
                "It excludes transcript text, subtitle text, narration text, reviewer note bodies, provider request or response bodies, object keys, local paths, tokens, secrets, API keys, and media bytes."
        );
    }

    private String markdown(
            String overall,
            String headline,
            String nextAction,
            int limit,
            int ready,
            int needsReview,
            int needsRender,
            int needsAuthoring,
            int blocked,
            int notApplicable,
            List<SessionNarrationProductionJobVo> jobs,
            List<SessionNarrationProductionCheckVo> checks,
            List<SessionNarrationProductionLinkVo> links,
            List<String> safetyNotes
    ) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# LinguaFrame Session Narration Production Board\n\n");
        markdown.append("- Overall: ").append(overall).append('\n');
        markdown.append("- Headline: ").append(headline).append('\n');
        markdown.append("- Next action: ").append(nextAction).append('\n');
        markdown.append("- Limit: ").append(limit).append('\n');
        markdown.append("- Ready to deliver: ").append(ready).append('\n');
        markdown.append("- Needs review: ").append(needsReview).append('\n');
        markdown.append("- Needs render: ").append(needsRender).append('\n');
        markdown.append("- Needs authoring: ").append(needsAuthoring).append('\n');
        markdown.append("- Blocked: ").append(blocked).append('\n');
        markdown.append("- Not applicable: ").append(notApplicable).append("\n\n");
        markdown.append("## Jobs\n\n");
        if (jobs.isEmpty()) {
            markdown.append("- No recent jobs found.\n");
        } else {
            jobs.forEach(job -> markdown.append("- ")
                    .append(job.classification()).append(": ")
                    .append(job.jobId())
                    .append(" status=").append(job.jobStatus())
                    .append(" segments=").append(job.segmentCount())
                    .append(" coverage=").append(job.coveragePercent())
                    .append(" audio=").append(job.audioReady())
                    .append(" video=").append(job.videoReady())
                    .append(" delivery=").append(job.deliveryReady())
                    .append(" next=").append(job.recommendedNextAction())
                    .append('\n'));
        }
        markdown.append("\n## Checks\n\n");
        checks.forEach(check -> markdown.append("- ")
                .append(check.label()).append(": ")
                .append(check.status()).append(" - ")
                .append(check.detail()).append('\n'));
        markdown.append("\n## Safe Links\n\n");
        links.forEach(link -> markdown.append("- ")
                .append(link.label()).append(": `")
                .append(link.href()).append("`\n"));
        markdown.append("\n## Safety Notes\n\n");
        safetyNotes.forEach(note -> markdown.append("- ").append(note).append('\n'));
        return markdown.toString();
    }

    private String headline(String overall, int ready, int needsReview, int needsRender, int needsAuthoring, int blocked) {
        if (BLOCKED.equals(overall)) {
            return blocked + " job(s) have blocked narration production evidence.";
        }
        if (ATTENTION.equals(overall)) {
            return needsReview + needsRender + needsAuthoring + " job(s) need narration production work.";
        }
        if (EMPTY.equals(overall)) {
            return "No recent jobs are available for narration production.";
        }
        return ready + " job(s) are ready for narration delivery.";
    }

    private String defaultNextAction(String overall) {
        if (BLOCKED.equals(overall)) {
            return "Open the first blocked narration production row.";
        }
        if (ATTENTION.equals(overall)) {
            return "Open needs-review, needs-render, or needs-authoring rows.";
        }
        if (EMPTY.equals(overall)) {
            return "Run a demo job, then return to narration production board.";
        }
        return "Download narration delivery package for the ready run.";
    }

    private int resolvedLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private int count(List<SessionNarrationProductionJobVo> jobs, String classification) {
        return (int) jobs.stream().filter(job -> classification.equals(job.classification())).count();
    }

    private int classificationRank(String classification) {
        return switch (classification) {
            case BLOCKED -> 0;
            case "NEEDS_REVIEW" -> 1;
            case "NEEDS_RENDER" -> 2;
            case "NEEDS_AUTHORING" -> 3;
            case "READY_TO_DELIVER" -> 4;
            case "NOT_APPLICABLE" -> 5;
            default -> 6;
        };
    }

    private String attention(String classification) {
        return switch (classification) {
            case "READY_TO_DELIVER" -> READY;
            case BLOCKED -> BLOCKED;
            default -> ATTENTION;
        };
    }
}
