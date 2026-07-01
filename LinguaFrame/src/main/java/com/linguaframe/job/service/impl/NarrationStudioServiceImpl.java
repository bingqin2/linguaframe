package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.vo.CustomNarrationRenderHandoffVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.DemoHandoffPortalVo;
import com.linguaframe.job.domain.vo.DemoReviewerWorkspaceVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.domain.vo.NarrationStudioLinkVo;
import com.linguaframe.job.domain.vo.NarrationStudioStepVo;
import com.linguaframe.job.domain.vo.NarrationStudioVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadVo;
import com.linguaframe.job.service.CustomNarrationRenderHandoffService;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.DemoHandoffPortalService;
import com.linguaframe.job.service.DemoReviewerWorkspaceService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationDeliveryPackageService;
import com.linguaframe.job.service.NarrationPlaybackReviewResolutionService;
import com.linguaframe.job.service.NarrationRenderReviewService;
import com.linguaframe.job.service.NarrationSceneBoardService;
import com.linguaframe.job.service.NarrationStudioService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import com.linguaframe.job.service.UploadNarrationLaunchpadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NarrationStudioServiceImpl implements NarrationStudioService {

    private final LocalizationJobQueryService queryService;
    private final NarrationWorkspaceService workspaceService;
    private final UploadNarrationLaunchpadService uploadNarrationLaunchpadService;
    private final NarrationSceneBoardService sceneBoardService;
    private final NarrationRenderReviewService renderReviewService;
    private final NarrationPlaybackReviewResolutionService playbackResolutionService;
    private final NarrationDeliveryPackageService deliveryPackageService;
    private final CustomNarrationRenderHandoffService customRenderHandoffService;
    private final DemoAcceptanceGateService acceptanceGateService;
    private final DemoReviewerWorkspaceService reviewerWorkspaceService;
    private final DemoHandoffPortalService handoffPortalService;
    private final Clock clock;

    @Autowired
    public NarrationStudioServiceImpl(
            LocalizationJobQueryService queryService,
            NarrationWorkspaceService workspaceService,
            UploadNarrationLaunchpadService uploadNarrationLaunchpadService,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            NarrationDeliveryPackageService deliveryPackageService,
            CustomNarrationRenderHandoffService customRenderHandoffService,
            DemoAcceptanceGateService acceptanceGateService,
            DemoReviewerWorkspaceService reviewerWorkspaceService,
            DemoHandoffPortalService handoffPortalService
    ) {
        this(
                queryService,
                workspaceService,
                uploadNarrationLaunchpadService,
                sceneBoardService,
                renderReviewService,
                playbackResolutionService,
                deliveryPackageService,
                customRenderHandoffService,
                acceptanceGateService,
                reviewerWorkspaceService,
                handoffPortalService,
                Clock.systemUTC()
        );
    }

    public NarrationStudioServiceImpl(
            LocalizationJobQueryService queryService,
            NarrationWorkspaceService workspaceService,
            UploadNarrationLaunchpadService uploadNarrationLaunchpadService,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            NarrationDeliveryPackageService deliveryPackageService,
            CustomNarrationRenderHandoffService customRenderHandoffService,
            DemoAcceptanceGateService acceptanceGateService,
            DemoReviewerWorkspaceService reviewerWorkspaceService,
            DemoHandoffPortalService handoffPortalService,
            Clock clock
    ) {
        this.queryService = queryService;
        this.workspaceService = workspaceService;
        this.uploadNarrationLaunchpadService = uploadNarrationLaunchpadService;
        this.sceneBoardService = sceneBoardService;
        this.renderReviewService = renderReviewService;
        this.playbackResolutionService = playbackResolutionService;
        this.deliveryPackageService = deliveryPackageService;
        this.customRenderHandoffService = customRenderHandoffService;
        this.acceptanceGateService = acceptanceGateService;
        this.reviewerWorkspaceService = reviewerWorkspaceService;
        this.handoffPortalService = handoffPortalService;
        this.clock = clock;
    }

    @Override
    public NarrationStudioVo getStudio(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        NarrationWorkspaceVo workspace = workspaceService.getWorkspace(jobId);
        UploadNarrationLaunchpadVo launchpad = uploadNarrationLaunchpadService.getLaunchpad(jobId);
        NarrationSceneBoardVo sceneBoard = sceneBoardService.getSceneBoard(jobId);
        NarrationRenderReviewVo renderReview = renderReviewService.getReview(jobId);
        NarrationPlaybackReviewResolutionVo playbackResolution = playbackResolutionService.getResolution(jobId);
        NarrationDeliveryPackageVo deliveryPackage = deliveryPackageService.getSummary(jobId);
        CustomNarrationRenderHandoffVo customRender = customRenderHandoffService.summarize(jobId);
        DemoAcceptanceGateVo acceptanceGate = acceptanceGateService.buildGate(jobId);
        DemoReviewerWorkspaceVo reviewerWorkspace = reviewerWorkspaceService.getWorkspace(jobId);
        DemoHandoffPortalVo handoffPortal = handoffPortalService.getPortal(jobId);

        List<NarrationStudioStepVo> steps = steps(
                jobId,
                workspace,
                launchpad,
                sceneBoard,
                renderReview,
                playbackResolution,
                deliveryPackage,
                customRender,
                acceptanceGate,
                reviewerWorkspace,
                handoffPortal
        );
        String overallStatus = overallStatus(workspace, sceneBoard, customRender, playbackResolution, deliveryPackage, acceptanceGate);
        return new NarrationStudioVo(
                job.jobId(),
                job.videoId(),
                Instant.now(clock),
                overallStatus,
                phase(overallStatus),
                recommendedNextAction(overallStatus, steps),
                workspace.segmentCount(),
                workspace.totalCharacterCount(),
                customRender.audioReady() || renderReview.audioReady() || deliveryPackage.audioReady(),
                customRender.videoReady() || renderReview.videoReady() || deliveryPackage.videoReady(),
                steps,
                links(jobId, customRender),
                safetyNotes()
        );
    }

    private List<NarrationStudioStepVo> steps(
            String jobId,
            NarrationWorkspaceVo workspace,
            UploadNarrationLaunchpadVo launchpad,
            NarrationSceneBoardVo sceneBoard,
            NarrationRenderReviewVo renderReview,
            NarrationPlaybackReviewResolutionVo playbackResolution,
            NarrationDeliveryPackageVo deliveryPackage,
            CustomNarrationRenderHandoffVo customRender,
            DemoAcceptanceGateVo acceptanceGate,
            DemoReviewerWorkspaceVo reviewerWorkspace,
            DemoHandoffPortalVo handoffPortal
    ) {
        return List.of(
                step("AUTHOR_ROWS", "Author rows", authorStatus(workspace, sceneBoard), authorDetail(workspace, launchpad, sceneBoard),
                        authorNextAction(workspace, sceneBoard), "/api/jobs/" + jobId + "/narration-workspace"),
                step("PREVIEW_TTS", "Preview TTS", workspace.segmentCount() > 0 ? "READY" : "EMPTY",
                        "Saved rows=" + workspace.segmentCount() + "; selected launchpad row=" + value(launchpad.selectedSegmentIndex()) + ".",
                        workspace.segmentCount() > 0 ? "Preview selected rows before committing provider spend." : "Add narration rows before previewing TTS.",
                        "/api/jobs/" + jobId + "/narration-segment-preview"),
                step("RENDER_CUSTOM", "Render custom narration", renderStatus(customRender), renderDetail(customRender, renderReview),
                        customRender.nextAction(), customRender.reportRoute()),
                step("REVIEW_PLAYBACK", "Review playback", reviewStatus(playbackResolution, customRender), reviewDetail(playbackResolution),
                        playbackResolution.nextAction(), "/api/jobs/" + jobId + "/narration-playback-review/resolution"),
                step("PACKAGE_DELIVERY", "Package delivery", packageStatus(deliveryPackage, customRender), packageDetail(deliveryPackage),
                        deliveryPackage.recommendedNextAction(), "/api/jobs/" + jobId + "/narration-delivery-package"),
                step("FINAL_HANDOFF", "Final handoff", finalStatus(acceptanceGate, reviewerWorkspace, handoffPortal, customRender),
                        "Acceptance=" + acceptanceGate.gateStatus()
                                + "; reviewer=" + reviewerWorkspace.overallStatus()
                                + "; portal=" + handoffPortal.overallStatus() + ".",
                        "READY".equals(acceptanceGate.gateStatus())
                                ? "Open reviewer workspace or handoff portal for final delivery."
                                : acceptanceGate.recommendedNextAction(),
                        "/api/jobs/" + jobId + "/demo-acceptance-gate")
        );
    }

    private String authorStatus(NarrationWorkspaceVo workspace, NarrationSceneBoardVo sceneBoard) {
        if (workspace.segmentCount() == 0) {
            return "EMPTY";
        }
        if ("BLOCKED".equals(sceneBoard.status()) || !workspace.generationReady()) {
            return "BLOCKED";
        }
        if ("READY".equals(sceneBoard.status())) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String authorDetail(
            NarrationWorkspaceVo workspace,
            UploadNarrationLaunchpadVo launchpad,
            NarrationSceneBoardVo sceneBoard
    ) {
        return "Saved rows=" + workspace.segmentCount()
                + "; characters=" + workspace.totalCharacterCount()
                + "; launchpad=" + launchpad.status()
                + "; sceneBoard=" + sceneBoard.status() + ".";
    }

    private String authorNextAction(NarrationWorkspaceVo workspace, NarrationSceneBoardVo sceneBoard) {
        if (workspace.segmentCount() == 0) {
            return "Add rows in the narration workspace or seed them from upload quick script.";
        }
        if ("READY".equals(sceneBoard.status()) && workspace.generationReady()) {
            return "Preview or render the saved narration rows.";
        }
        return "Resolve scene-board or workspace validation issues before rendering.";
    }

    private String renderStatus(CustomNarrationRenderHandoffVo customRender) {
        if ("NOT_APPLICABLE".equals(customRender.status())) {
            return "EMPTY";
        }
        return customRender.status();
    }

    private String renderDetail(CustomNarrationRenderHandoffVo customRender, NarrationRenderReviewVo renderReview) {
        return "Custom render=" + customRender.status()
                + "; outputPlan=" + customRender.outputPlan()
                + "; review=" + renderReview.status()
                + "; audioReady=" + customRender.audioReady()
                + "; videoReady=" + customRender.videoReady() + ".";
    }

    private String reviewStatus(NarrationPlaybackReviewResolutionVo resolution, CustomNarrationRenderHandoffVo customRender) {
        if (resolution.segmentCount() == 0) {
            return "EMPTY";
        }
        if (!customRender.audioReady() && !"BLOCKED".equals(resolution.status())) {
            return "ATTENTION";
        }
        return "READY".equals(resolution.status()) ? "READY" : "BLOCKED";
    }

    private String reviewDetail(NarrationPlaybackReviewResolutionVo resolution) {
        return "Resolution=" + resolution.status()
                + "; unresolved=" + resolution.unresolvedSegmentCount()
                + "; textRevision=" + resolution.textRevisionRequiredCount()
                + "; rerender=" + resolution.rerenderRequiredCount()
                + "; unreviewed=" + resolution.unreviewedSegmentCount() + ".";
    }

    private String packageStatus(NarrationDeliveryPackageVo deliveryPackage, CustomNarrationRenderHandoffVo customRender) {
        if ("EMPTY".equals(deliveryPackage.status())) {
            return "EMPTY";
        }
        if (!customRender.audioReady() && !"BLOCKED".equals(customRender.status())) {
            return "ATTENTION";
        }
        return deliveryPackage.status();
    }

    private String packageDetail(NarrationDeliveryPackageVo deliveryPackage) {
        return "Delivery=" + deliveryPackage.status()
                + "; audioReady=" + deliveryPackage.audioReady()
                + "; videoReady=" + deliveryPackage.videoReady()
                + "; entries=" + deliveryPackage.packageEntries().size() + ".";
    }

    private String finalStatus(
            DemoAcceptanceGateVo acceptanceGate,
            DemoReviewerWorkspaceVo reviewerWorkspace,
            DemoHandoffPortalVo handoffPortal,
            CustomNarrationRenderHandoffVo customRender
    ) {
        if (!customRender.audioReady() && !"BLOCKED".equals(customRender.status())) {
            return "ATTENTION";
        }
        if ("BLOCKED".equals(acceptanceGate.gateStatus())
                || "BLOCKED".equals(reviewerWorkspace.overallStatus())
                || "BLOCKED".equals(handoffPortal.overallStatus())) {
            return "BLOCKED";
        }
        if ("READY".equals(acceptanceGate.gateStatus())
                && "READY".equals(reviewerWorkspace.overallStatus())
                && "READY".equals(handoffPortal.overallStatus())) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String overallStatus(
            NarrationWorkspaceVo workspace,
            NarrationSceneBoardVo sceneBoard,
            CustomNarrationRenderHandoffVo customRender,
            NarrationPlaybackReviewResolutionVo playbackResolution,
            NarrationDeliveryPackageVo deliveryPackage,
            DemoAcceptanceGateVo acceptanceGate
    ) {
        if (workspace.segmentCount() == 0) {
            return "EMPTY";
        }
        boolean customRenderReady = customRender.audioReady() || customRender.videoReady();
        if ("BLOCKED".equals(sceneBoard.status())
                || "BLOCKED".equals(customRender.status())
                || (customRenderReady && "BLOCKED".equals(deliveryPackage.status()))
                || (customRenderReady && "BLOCKED".equals(acceptanceGate.gateStatus()))
                || (customRenderReady && playbackResolution.segmentCount() > 0 && !"READY".equals(playbackResolution.status()))) {
            return "BLOCKED";
        }
        if (customRender.audioReady()
                && ("READY".equals(deliveryPackage.status()) || deliveryPackage.audioReady())
                && "READY".equals(acceptanceGate.gateStatus())) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String phase(String status) {
        return switch (status) {
            case "READY" -> "NARRATION_STUDIO_READY";
            case "BLOCKED" -> "NARRATION_STUDIO_BLOCKED";
            case "EMPTY" -> "NARRATION_STUDIO_EMPTY";
            default -> "NARRATION_STUDIO_NEEDS_ACTION";
        };
    }

    private String recommendedNextAction(String overallStatus, List<NarrationStudioStepVo> steps) {
        if ("READY".equals(overallStatus)) {
            return "Open the final handoff links or share the delivery package.";
        }
        return steps.stream()
                .filter(step -> !"READY".equals(step.status()))
                .findFirst()
                .map(NarrationStudioStepVo::nextAction)
                .orElse("Review narration studio steps before final handoff.");
    }

    private List<NarrationStudioLinkVo> links(String jobId, CustomNarrationRenderHandoffVo customRender) {
        Map<String, NarrationStudioLinkVo> links = new LinkedHashMap<>();
        add(links, link("NARRATION_WORKSPACE", "Narration workspace", "/api/jobs/" + jobId + "/narration-workspace", "application/json", "Saved narration workspace metadata."));
        add(links, link("UPLOAD_NARRATION_LAUNCHPAD", "Upload narration launchpad", "/api/jobs/" + jobId + "/upload-narration-launchpad", "application/json", "Upload-seeded narration launchpad."));
        add(links, link("NARRATION_SCENE_BOARD", "Narration scene board", "/api/jobs/" + jobId + "/narration-scene-board", "application/json", "Scene-board readiness metadata."));
        add(links, link("CUSTOM_NARRATION_RENDER_REPORT", "Custom narration render report", customRender.reportRoute(), "text/markdown", "Custom render report."));
        add(links, link("NARRATION_RENDER_REVIEW", "Narration render review", "/api/jobs/" + jobId + "/narration-render-review", "application/json", "Render review cue sheet."));
        add(links, link("NARRATION_PLAYBACK_RESOLUTION", "Playback resolution", "/api/jobs/" + jobId + "/narration-playback-review/resolution", "application/json", "Playback resolution gate."));
        add(links, link("NARRATION_DELIVERY_PACKAGE", "Narration delivery package", "/api/jobs/" + jobId + "/narration-delivery-package", "application/json", "Narration delivery package."));
        add(links, link("DEMO_ACCEPTANCE_GATE", "Demo acceptance gate", "/api/jobs/" + jobId + "/demo-acceptance-gate", "application/json", "Final acceptance gate."));
        add(links, link("DEMO_REVIEWER_WORKSPACE", "Demo reviewer workspace", "/api/jobs/" + jobId + "/demo-reviewer-workspace", "application/json", "Reviewer workspace."));
        add(links, link("DEMO_HANDOFF_PORTAL", "Demo handoff portal", "/api/jobs/" + jobId + "/demo-handoff-portal", "application/json", "Offline handoff portal."));
        return List.copyOf(links.values());
    }

    private void add(Map<String, NarrationStudioLinkVo> links, NarrationStudioLinkVo link) {
        links.putIfAbsent(link.kind() + ":" + link.href(), link);
    }

    private NarrationStudioLinkVo link(
            String kind,
            String label,
            String href,
            String contentType,
            String description
    ) {
        return new NarrationStudioLinkVo(kind, label, href, contentType, description);
    }

    private NarrationStudioStepVo step(
            String key,
            String label,
            String status,
            String detail,
            String nextAction,
            String safeLink
    ) {
        return new NarrationStudioStepVo(key, label, status, detail, nextAction, safeLink);
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration studio is read-only and generated from existing safe evidence services.",
                "It does not save rows, call providers, run FFmpeg, upload media, create artifacts, or mutate object storage.",
                "Narration text, reviewer notes, transcripts, subtitles, local paths, object keys, provider payloads, secrets, and media bytes are excluded."
        );
    }

    private String value(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }
}
