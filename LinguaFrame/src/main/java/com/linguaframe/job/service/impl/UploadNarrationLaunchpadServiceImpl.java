package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.vo.NarrationSceneBoardCheckVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardLinkVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadActionVo;
import com.linguaframe.job.domain.vo.UploadNarrationLaunchpadVo;
import com.linguaframe.job.service.NarrationSceneBoardService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import com.linguaframe.job.service.UploadNarrationLaunchpadService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class UploadNarrationLaunchpadServiceImpl implements UploadNarrationLaunchpadService {

    private static final String STATUS_READY = "READY";
    private static final String STATUS_BLOCKED = "BLOCKED";
    private static final String STATUS_ATTENTION = "ATTENTION";
    private static final String STATUS_NOT_APPLICABLE = "NOT_APPLICABLE";

    private final NarrationWorkspaceService narrationWorkspaceService;
    private final NarrationSceneBoardService narrationSceneBoardService;
    private final NarrationVoiceCatalogService narrationVoiceCatalogService;

    public UploadNarrationLaunchpadServiceImpl(
            NarrationWorkspaceService narrationWorkspaceService,
            NarrationSceneBoardService narrationSceneBoardService,
            NarrationVoiceCatalogService narrationVoiceCatalogService
    ) {
        this.narrationWorkspaceService = narrationWorkspaceService;
        this.narrationSceneBoardService = narrationSceneBoardService;
        this.narrationVoiceCatalogService = narrationVoiceCatalogService;
    }

    @Override
    public UploadNarrationLaunchpadVo getLaunchpad(String jobId) {
        NarrationWorkspaceVo workspace = narrationWorkspaceService.getWorkspace(jobId);
        NarrationSceneBoardVo sceneBoard = narrationSceneBoardService.getSceneBoard(jobId);
        NarrationVoiceCatalogVo catalog = narrationVoiceCatalogService.catalog();
        int blockingIssueCount = countChecks(sceneBoard, STATUS_BLOCKED);
        int attentionIssueCount = countChecks(sceneBoard, STATUS_ATTENTION);
        String status = status(workspace, blockingIssueCount, attentionIssueCount);

        return new UploadNarrationLaunchpadVo(
                jobId,
                Instant.now(),
                status,
                nextAction(status),
                workspace.segmentCount(),
                workspace.totalCharacterCount(),
                workspace.totalDurationSeconds(),
                workspace.segmentCount() > 0 ? 0 : null,
                catalog.provider(),
                catalog.defaultVoice(),
                voiceSummary(workspace, sceneBoard),
                sceneBoard.status(),
                blockingIssueCount,
                attentionIssueCount,
                sceneBoard.audioReady(),
                sceneBoard.videoReady(),
                actions(jobId, status),
                safeLinks(jobId),
                List.of(
                        "Upload narration launchpad is metadata-only and read-only.",
                        "TTS preview and render actions stay explicit and may consume provider credits.",
                        "Launchpad output excludes narration text, object keys, local paths, provider payloads, tokens, API keys, and media bytes."
                )
        );
    }

    private String status(NarrationWorkspaceVo workspace, int blockingIssueCount, int attentionIssueCount) {
        if (workspace.segmentCount() == 0) {
            return STATUS_NOT_APPLICABLE;
        }
        if (blockingIssueCount > 0) {
            return STATUS_BLOCKED;
        }
        if (attentionIssueCount > 0) {
            return STATUS_ATTENTION;
        }
        return STATUS_READY;
    }

    private String nextAction(String status) {
        return switch (status) {
            case STATUS_NOT_APPLICABLE -> "Add narration rows in the workspace or paste a quick script during upload.";
            case STATUS_BLOCKED -> "Fix blocked narration rows in the workspace before previewing or rendering.";
            case STATUS_ATTENTION -> "Review narration warnings, then preview selected-row TTS explicitly.";
            default -> "Preview selected-row TTS explicitly, then run render preflight when ready.";
        };
    }

    private List<UploadNarrationLaunchpadActionVo> actions(String jobId, String status) {
        List<UploadNarrationLaunchpadActionVo> actions = new ArrayList<>();
        actions.add(action(
                "open-workspace",
                "Open narration workspace",
                "Review and edit saved narration rows.",
                "/api/jobs/" + jobId + "/narration-workspace",
                "Open this job in the browser narration workspace."
        ));
        if (!STATUS_NOT_APPLICABLE.equals(status)) {
            actions.add(action(
                    "preview-tts",
                    "Preview selected-row TTS",
                    "Use the existing preview control; this remains an explicit provider-cost action.",
                    "/api/jobs/" + jobId + "/narration-workspace/segment-preview",
                    "Preview from the browser Narration TTS preview panel."
            ));
            actions.add(action(
                    "render-preflight",
                    "Run render preflight",
                    "Inspect narration render readiness before generating media.",
                    "/api/jobs/" + jobId + "/narration-demo-render-preflight",
                    "LINGUAFRAME_DEMO_JOB_ID=" + jobId + " scripts/demo/narration-demo-render-preflight.sh"
            ));
            actions.add(action(
                    "render-review",
                    "Open render review",
                    "Review narration cue-sheet metadata after save or render.",
                    "/api/jobs/" + jobId + "/narration-render-review",
                    "LINGUAFRAME_DEMO_JOB_ID=" + jobId + " scripts/demo/narration-render-review.sh"
            ));
        }
        return actions;
    }

    private UploadNarrationLaunchpadActionVo action(
            String key,
            String label,
            String description,
            String href,
            String command
    ) {
        return new UploadNarrationLaunchpadActionVo(key, label, description, href, command);
    }

    private List<NarrationSceneBoardLinkVo> safeLinks(String jobId) {
        return List.of(
                new NarrationSceneBoardLinkVo("workspace", "/api/jobs/" + jobId + "/narration-workspace", "Narration workspace"),
                new NarrationSceneBoardLinkVo("scene-board", "/api/jobs/" + jobId + "/narration-scene-board", "Narration scene board"),
                new NarrationSceneBoardLinkVo("render-preflight", "/api/jobs/" + jobId + "/narration-demo-render-preflight", "Narration render preflight"),
                new NarrationSceneBoardLinkVo("render-review", "/api/jobs/" + jobId + "/narration-render-review", "Narration render review")
        );
    }

    private int countChecks(NarrationSceneBoardVo sceneBoard, String status) {
        return (int) sceneBoard.checks().stream()
                .filter(check -> status.equals(check.status()))
                .count();
    }

    private String voiceSummary(NarrationWorkspaceVo workspace, NarrationSceneBoardVo sceneBoard) {
        if (workspace.segmentCount() == 0) {
            return "none";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        sceneBoard.segments().forEach(segment -> counts.merge(normalizeVoiceState(segment.voiceState()), 1, Integer::sum));
        return counts.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String normalizeVoiceState(String voiceState) {
        if (voiceState == null || voiceState.isBlank() || "Inherit default".equals(voiceState)) {
            return "inherited";
        }
        return voiceState.trim();
    }
}
