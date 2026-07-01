package com.linguaframe.job.service.impl;

import com.linguaframe.common.config.LinguaFrameProperties;
import com.linguaframe.job.domain.dto.CustomNarrationRenderDto;
import com.linguaframe.job.domain.dto.CustomNarrationRenderPreflightDto;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.CustomNarrationRenderCheckVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderPreflightVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderStepVo;
import com.linguaframe.job.domain.vo.CustomNarrationRenderVo;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarratedVideoGenerationVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationGenerationVo;
import com.linguaframe.job.domain.vo.NarrationDeliveryPackageVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.CustomNarrationRenderConsoleService;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarratedVideoService;
import com.linguaframe.job.service.NarrationAudioService;
import com.linguaframe.job.service.NarrationDeliveryPackageService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationPlaybackReviewService;
import com.linguaframe.job.service.NarrationRenderReviewService;
import com.linguaframe.job.service.NarrationSceneBoardService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class CustomNarrationRenderConsoleServiceImpl implements CustomNarrationRenderConsoleService {

    private static final String READY = "READY";
    private static final String ATTENTION = "ATTENTION";
    private static final String BLOCKED = "BLOCKED";

    private final LinguaFrameProperties properties;
    private final NarrationWorkspaceService workspaceService;
    private final NarrationSceneBoardService sceneBoardService;
    private final NarrationRenderReviewService renderReviewService;
    private final NarrationEvidenceService evidenceService;
    private final JobArtifactService artifactService;
    private final NarrationAudioService narrationAudioService;
    private final NarratedVideoService narratedVideoService;
    private final NarrationPlaybackReviewService playbackReviewService;
    private final NarrationDeliveryPackageService deliveryPackageService;

    @Autowired
    public CustomNarrationRenderConsoleServiceImpl(
            LinguaFrameProperties properties,
            NarrationWorkspaceService workspaceService,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        this(
                properties,
                workspaceService,
                sceneBoardService,
                renderReviewService,
                evidenceService,
                artifactService,
                null,
                null,
                null,
                null
        );
    }

    public CustomNarrationRenderConsoleServiceImpl(
            LinguaFrameProperties properties,
            NarrationWorkspaceService workspaceService,
            NarrationSceneBoardService sceneBoardService,
            NarrationRenderReviewService renderReviewService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService,
            NarrationAudioService narrationAudioService,
            NarratedVideoService narratedVideoService,
            NarrationPlaybackReviewService playbackReviewService,
            NarrationDeliveryPackageService deliveryPackageService
    ) {
        this.properties = properties;
        this.workspaceService = workspaceService;
        this.sceneBoardService = sceneBoardService;
        this.renderReviewService = renderReviewService;
        this.evidenceService = evidenceService;
        this.artifactService = artifactService;
        this.narrationAudioService = narrationAudioService;
        this.narratedVideoService = narratedVideoService;
        this.playbackReviewService = playbackReviewService;
        this.deliveryPackageService = deliveryPackageService;
    }

    @Override
    public CustomNarrationRenderPreflightVo preflight(String jobId, CustomNarrationRenderPreflightDto request) {
        CustomNarrationRenderPreflightDto safeRequest = request == null
                ? new CustomNarrationRenderPreflightDto(true, false, false)
                : request;
        NarrationWorkspaceVo workspace = workspaceService.getWorkspace(jobId);
        NarrationSceneBoardVo sceneBoard = sceneBoardService.getSceneBoard(jobId);
        NarrationRenderReviewVo renderReview = renderReviewService.getReview(jobId);
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);

        List<CustomNarrationRenderCheckVo> checks = new ArrayList<>();
        List<String> acknowledgements = new ArrayList<>();

        int segmentCount = workspace == null ? 0 : workspace.segmentCount();
        int characterCount = workspace == null ? 0 : workspace.totalCharacterCount();
        BigDecimal totalNarrationSeconds = workspace == null || workspace.totalDurationSeconds() == null
                ? BigDecimal.ZERO
                : workspace.totalDurationSeconds();

        if (segmentCount > 0 && workspace != null && workspace.generationReady()) {
            checks.add(check("WORKSPACE", "Saved workspace", "PASS", "Saved narration workspace has " + segmentCount + " renderable rows."));
        } else {
            checks.add(check("WORKSPACE", "Saved workspace", "BLOCK", "Save at least one narration row before rendering."));
        }

        if (hasBlockedSceneBoardCheck(sceneBoard)) {
            checks.add(check("SCENE_BOARD", "Scene board", "BLOCK", "Resolve blocking scene-board checks before rendering."));
        } else if (sceneBoard != null && BLOCKED.equals(sceneBoard.status())) {
            checks.add(check("SCENE_BOARD", "Scene board", "BLOCK", "Scene board status is BLOCKED."));
        } else if (sceneBoard != null && READY.equals(sceneBoard.status())) {
            checks.add(check("SCENE_BOARD", "Scene board", "PASS", "Scene board is ready for render."));
        } else {
            checks.add(check("SCENE_BOARD", "Scene board", "WARN", "Review scene-board status before render."));
        }

        String providerMode = providerMode();
        boolean paidProvider = !"demo".equals(providerMode);
        if (paidProvider && !safeRequest.acknowledgeProviderCost()) {
            acknowledgements.add("PROVIDER_COST");
            checks.add(check("TTS_PROVIDER", "TTS provider", "BLOCK", "Acknowledge configured " + providerMode + " TTS provider cost before render."));
        } else if (paidProvider) {
            acknowledgements.add("PROVIDER_COST");
            checks.add(check("TTS_PROVIDER", "TTS provider", "WARN", "Render can call the configured " + providerMode + " TTS provider."));
        } else {
            checks.add(check("TTS_PROVIDER", "TTS provider", "PASS", "Demo TTS provider is configured."));
        }

        if (safeRequest.generateNarratedVideo()) {
            if (!safeRequest.acknowledgeVideoRender()) {
                acknowledgements.add("VIDEO_RENDER");
                checks.add(check("VIDEO_ACKNOWLEDGEMENT", "Video acknowledgement", "BLOCK", "Acknowledge FFmpeg narrated-video generation before render."));
            } else {
                acknowledgements.add("VIDEO_RENDER");
                checks.add(check("VIDEO_ACKNOWLEDGEMENT", "Video acknowledgement", "PASS", "Narrated-video generation was acknowledged."));
            }
            if (hasBaseVideo(artifacts)) {
                checks.add(check("NARRATED_VIDEO_INPUT", "Narrated video input", "PASS", "A base video artifact is available."));
            } else {
                checks.add(check("NARRATED_VIDEO_INPUT", "Narrated video input", "BLOCK", "Generate or provide a base video before narrated-video render."));
            }
        } else {
            checks.add(check("VIDEO_ACKNOWLEDGEMENT", "Video acknowledgement", "WARN", "Narrated video generation is disabled; render will generate audio only."));
            checks.add(check("NARRATED_VIDEO_INPUT", "Narrated video input", "WARN", "Narrated video generation is disabled."));
        }

        return new CustomNarrationRenderPreflightVo(
                jobId,
                overallStatus(checks),
                List.copyOf(checks),
                segmentCount,
                characterCount,
                totalNarrationSeconds,
                voiceSummary(renderReview, evidence),
                sceneBoard == null ? "UNKNOWN" : sceneBoard.status(),
                renderReview == null ? "UNKNOWN" : renderReview.status(),
                evidence == null ? "UNKNOWN" : evidence.status(),
                providerMode,
                paidProvider,
                safeRequest.generateNarratedVideo(),
                renderReview != null && renderReview.audioReady(),
                renderReview != null && renderReview.videoReady(),
                List.copyOf(acknowledgements),
                "LINGUAFRAME_DEMO_JOB_ID=" + jobId + " scripts/demo/custom-narration-render.sh",
                List.of(
                        "/api/jobs/" + jobId + "/custom-narration-render",
                        "/api/jobs/" + jobId + "/custom-narration-render/markdown/download",
                        "/api/jobs/" + jobId + "/narration-render-review",
                        "/api/jobs/" + jobId + "/narration-evidence"
                ),
                List.of("Metadata-only preflight. Narration text, notes, paths, object keys, provider payloads, and secrets are omitted.")
        );
    }

    @Override
    public CustomNarrationRenderVo render(String jobId, CustomNarrationRenderDto request) {
        if (narrationAudioService == null || narratedVideoService == null || playbackReviewService == null || deliveryPackageService == null) {
            throw new IllegalStateException("Custom narration render dependencies are not configured.");
        }
        CustomNarrationRenderDto safeRequest = request == null
                ? new CustomNarrationRenderDto(true, false, false)
                : request;
        CustomNarrationRenderPreflightVo preflight = preflight(
                jobId,
                new CustomNarrationRenderPreflightDto(
                        safeRequest.generateNarratedVideo(),
                        safeRequest.acknowledgeProviderCost(),
                        safeRequest.acknowledgeVideoRender()
                )
        );

        List<CustomNarrationRenderStepVo> steps = new ArrayList<>();
        if (BLOCKED.equals(preflight.status())) {
            steps.add(step("PREFLIGHT", "Run preflight", "FAILED", "Preflight is BLOCKED; render was not started."));
            steps.add(step("NARRATION_AUDIO", "Generate narration audio", "SKIPPED", "Skipped because preflight is blocked."));
            steps.add(step("NARRATED_VIDEO", "Generate narrated video", "SKIPPED", "Skipped because preflight is blocked."));
            return new CustomNarrationRenderVo(
                    jobId,
                    "FAILED",
                    safeRequest.generateNarratedVideo(),
                    preflight,
                    List.copyOf(steps),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0,
                    "Resolve blocking preflight checks before rendering."
            );
        }

        steps.add(step("PREFLIGHT", "Run preflight", "SUCCEEDED", "Preflight returned " + preflight.status() + "."));
        NarrationGenerationVo narrationAudio = null;
        NarratedVideoGenerationVo narratedVideo = null;
        boolean audioFailed = false;
        boolean videoFailed = false;

        try {
            narrationAudio = narrationAudioService.generateAudio(jobId);
            steps.add(step("NARRATION_AUDIO", "Generate narration audio", "SUCCEEDED", "Generated " + narrationAudio.filename() + "."));
        } catch (RuntimeException ex) {
            audioFailed = true;
            steps.add(step("NARRATION_AUDIO", "Generate narration audio", "FAILED", safeMessage(ex)));
            steps.add(step("NARRATED_VIDEO", "Generate narrated video", "SKIPPED", "Skipped because narration audio generation failed."));
        }

        if (!audioFailed) {
            if (safeRequest.generateNarratedVideo()) {
                try {
                    narratedVideo = narratedVideoService.generateVideo(jobId);
                    steps.add(step("NARRATED_VIDEO", "Generate narrated video", "SUCCEEDED", "Generated " + narratedVideo.filename() + "."));
                } catch (RuntimeException ex) {
                    videoFailed = true;
                    steps.add(step("NARRATED_VIDEO", "Generate narrated video", "FAILED", safeMessage(ex)));
                }
            } else {
                steps.add(step("NARRATED_VIDEO", "Generate narrated video", "SKIPPED", "Narrated video generation was disabled."));
            }
        }

        NarrationRenderReviewVo renderReview = renderReviewService.getReview(jobId);
        steps.add(step("RENDER_REVIEW", "Refresh render review", "SUCCEEDED", "Render review refreshed."));
        NarrationPlaybackReviewVo playbackReview = playbackReviewService.getReview(jobId);
        steps.add(step("PLAYBACK_REVIEW", "Refresh playback review", "SUCCEEDED", "Playback review refreshed."));
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        steps.add(step("NARRATION_EVIDENCE", "Refresh narration evidence", "SUCCEEDED", "Narration evidence refreshed."));
        NarrationDeliveryPackageVo deliveryPackage = deliveryPackageService.getPackage(jobId);
        steps.add(step("DELIVERY_PACKAGE", "Refresh delivery package", "SUCCEEDED", "Delivery package refreshed."));

        int generatedArtifactCount = (narrationAudio == null ? 0 : 1) + (narratedVideo == null ? 0 : 1);
        return new CustomNarrationRenderVo(
                jobId,
                overallRenderStatus(audioFailed, videoFailed),
                safeRequest.generateNarratedVideo(),
                preflight,
                List.copyOf(steps),
                narrationAudio,
                narratedVideo,
                renderReview,
                playbackReview,
                evidence,
                deliveryPackage,
                generatedArtifactCount,
                nextAction(audioFailed, videoFailed)
        );
    }

    private boolean hasBlockedSceneBoardCheck(NarrationSceneBoardVo sceneBoard) {
        return sceneBoard != null
                && sceneBoard.checks() != null
                && sceneBoard.checks().stream().anyMatch(check -> "BLOCK".equals(check.status()));
    }

    private String voiceSummary(NarrationRenderReviewVo renderReview, NarrationEvidenceVo evidence) {
        if (renderReview != null && StringUtils.hasText(renderReview.voiceSummary())) {
            return renderReview.voiceSummary();
        }
        if (evidence != null && StringUtils.hasText(evidence.voiceSummary())) {
            return evidence.voiceSummary();
        }
        return "none";
    }

    private String providerMode() {
        String provider = properties.getTts().getProvider();
        if (!StringUtils.hasText(provider)) {
            return "demo";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasBaseVideo(List<JobArtifactVo> artifacts) {
        return artifacts.stream().anyMatch(artifact ->
                artifact.type() == JobArtifactType.BURNED_VIDEO
                        || artifact.type() == JobArtifactType.DUBBED_VIDEO
                        || artifact.type() == JobArtifactType.REVIEWED_BURNED_VIDEO
        );
    }

    private String overallStatus(List<CustomNarrationRenderCheckVo> checks) {
        if (checks.stream().anyMatch(check -> "BLOCK".equals(check.status()))) {
            return BLOCKED;
        }
        if (checks.stream().anyMatch(check -> "WARN".equals(check.status()))) {
            return ATTENTION;
        }
        return READY;
    }

    private CustomNarrationRenderCheckVo check(String key, String label, String status, String message) {
        return new CustomNarrationRenderCheckVo(key, label, status, message);
    }

    private CustomNarrationRenderStepVo step(String key, String label, String status, String message) {
        return new CustomNarrationRenderStepVo(key, label, status, message);
    }

    private String overallRenderStatus(boolean audioFailed, boolean videoFailed) {
        if (audioFailed) {
            return "FAILED";
        }
        if (videoFailed) {
            return "PARTIAL";
        }
        return READY;
    }

    private String nextAction(boolean audioFailed, boolean videoFailed) {
        if (audioFailed) {
            return "Fix narration audio generation and retry.";
        }
        if (videoFailed) {
            return "Review video render failure and retry after fixing the base media.";
        }
        return "Review refreshed narration evidence and delivery package.";
    }

    private String safeMessage(RuntimeException ex) {
        if (StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }
}
