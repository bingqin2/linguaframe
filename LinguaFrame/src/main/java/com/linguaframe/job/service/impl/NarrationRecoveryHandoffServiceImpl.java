package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.StoredNarrationRecoveryHandoffPackageBo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateCheckVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateRunbookStepVo;
import com.linguaframe.job.domain.vo.DemoAcceptanceGateVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionSegmentVo;
import com.linguaframe.job.domain.vo.NarrationPlaybackReviewResolutionVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffCheckVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffLinkVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffStepVo;
import com.linguaframe.job.domain.vo.NarrationRecoveryHandoffVo;
import com.linguaframe.job.service.DemoAcceptanceGateService;
import com.linguaframe.job.service.NarrationPlaybackReviewResolutionService;
import com.linguaframe.job.service.NarrationRecoveryHandoffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NarrationRecoveryHandoffServiceImpl implements NarrationRecoveryHandoffService {

    private static final String JSON_ENTRY = "narration-recovery-handoff.json";
    private static final String MARKDOWN_ENTRY = "narration-recovery-handoff.md";
    private static final String ACCEPTANCE_ENTRY = "acceptance-gate.json";
    private static final String RESOLUTION_ENTRY = "playback-resolution.json";
    private static final String README_ENTRY = "README.md";
    private static final String MANIFEST_ENTRY = "manifest.json";

    private final DemoAcceptanceGateService acceptanceGateService;
    private final NarrationPlaybackReviewResolutionService playbackResolutionService;
    private final Clock clock;

    @Autowired
    public NarrationRecoveryHandoffServiceImpl(
            DemoAcceptanceGateService acceptanceGateService,
            NarrationPlaybackReviewResolutionService playbackResolutionService
    ) {
        this(acceptanceGateService, playbackResolutionService, Clock.systemUTC());
    }

    public NarrationRecoveryHandoffServiceImpl(
            DemoAcceptanceGateService acceptanceGateService,
            NarrationPlaybackReviewResolutionService playbackResolutionService,
            Clock clock
    ) {
        this.acceptanceGateService = acceptanceGateService;
        this.playbackResolutionService = playbackResolutionService;
        this.clock = clock;
    }

    @Override
    public NarrationRecoveryHandoffVo getHandoff(String jobId) {
        DemoAcceptanceGateVo gate = acceptanceGateService.buildGate(jobId);
        NarrationPlaybackReviewResolutionVo resolution = playbackResolutionService.getResolution(jobId);
        boolean narrationBlocked = gate.checks().stream()
                .anyMatch(check -> "NARRATION_PLAYBACK_RESOLVED".equals(check.key()) && "FAIL".equals(check.status()) && check.required());
        String status = status(narrationBlocked, resolution);
        return new NarrationRecoveryHandoffVo(
                gate.jobId(),
                gate.videoId(),
                Instant.now(clock),
                status,
                phase(status),
                headline(status),
                recommendedNextAction(status),
                gate.gateStatus(),
                resolution.status(),
                resolution.unresolvedSegmentCount(),
                resolution.textRevisionRequiredCount(),
                resolution.rerenderRequiredCount(),
                resolution.unreviewedSegmentCount(),
                resolution.audioReady(),
                resolution.videoReady(),
                checks(gate, resolution),
                steps(gate, resolution),
                safeLinks(jobId),
                packageEntries(),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationRecoveryHandoffVo handoff = getHandoff(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# LinguaFrame Narration Recovery Handoff");
        lines.add("");
        lines.add("## Summary");
        lines.add("- Job: " + value(handoff.jobId()));
        lines.add("- Video: " + value(handoff.videoId()));
        lines.add("- Status: " + value(handoff.status()));
        lines.add("- Phase: " + value(handoff.phase()));
        lines.add("- Acceptance gate: " + value(handoff.acceptanceGateStatus()));
        lines.add("- Playback resolution: " + value(handoff.playbackResolutionStatus()));
        lines.add("- Unresolved segments: " + handoff.unresolvedSegmentCount());
        lines.add("- Text revisions required: " + handoff.textRevisionRequiredCount());
        lines.add("- Rerenders required: " + handoff.rerenderRequiredCount());
        lines.add("- Unreviewed segments: " + handoff.unreviewedSegmentCount());
        lines.add("- Narration audio ready: " + handoff.audioReady());
        lines.add("- Narrated video ready: " + handoff.videoReady());
        lines.add("- Recommended next action: " + value(handoff.recommendedNextAction()));
        lines.add("");
        lines.add("## Checks");
        for (NarrationRecoveryHandoffCheckVo check : handoff.checks()) {
            lines.add("- " + check.label() + ": " + check.status()
                    + " - " + value(check.detail())
                    + " Next: " + value(check.nextAction()));
        }
        lines.add("");
        lines.add("## Recovery Steps");
        if (handoff.steps().isEmpty()) {
            lines.add("- No recovery steps are required.");
        } else {
            for (NarrationRecoveryHandoffStepVo step : handoff.steps()) {
                lines.add("- " + step.label() + ": " + step.status()
                        + " - " + value(step.action())
                        + " Command: " + value(step.safeCommand())
                        + " Link: " + value(step.safeLink()));
            }
        }
        lines.add("");
        lines.add("## Package Inventory");
        for (String entry : handoff.packageEntries()) {
            lines.add("- " + value(entry));
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationRecoveryHandoffLinkVo link : handoff.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : handoff.safetyNotes()) {
            lines.add("- " + value(note));
        }
        lines.add("");
        return String.join("\n", lines);
    }

    @Override
    public StoredNarrationRecoveryHandoffPackageBo openPackage(String jobId) {
        NarrationRecoveryHandoffVo handoff = getHandoff(jobId);
        DemoAcceptanceGateVo gate = acceptanceGateService.buildGate(jobId);
        NarrationPlaybackReviewResolutionVo resolution = playbackResolutionService.getResolution(jobId);
        String markdown = renderMarkdown(jobId);
        byte[] content = zipBytes(handoff, gate, resolution, markdown);
        return new StoredNarrationRecoveryHandoffPackageBo(
                "linguaframe-job-" + handoff.jobId() + "-narration-recovery-handoff.zip",
                "application/zip",
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private List<NarrationRecoveryHandoffCheckVo> checks(
            DemoAcceptanceGateVo gate,
            NarrationPlaybackReviewResolutionVo resolution
    ) {
        List<NarrationRecoveryHandoffCheckVo> checks = new ArrayList<>();
        checks.add(check(
                "ACCEPTANCE_GATE",
                "Acceptance gate",
                statusFromGate(gate.gateStatus()),
                "Acceptance gate status is " + gate.gateStatus() + ".",
                gate.recommendedNextAction(),
                true
        ));
        checks.add(check(
                "PLAYBACK_RESOLUTION",
                "Playback resolution",
                statusFromResolution(resolution.status()),
                "Playback resolution status is " + resolution.status() + "; unresolved=" + resolution.unresolvedSegmentCount() + ".",
                resolution.nextAction(),
                true
        ));
        checks.add(check(
                "NARRATION_AUDIO_READY",
                "Narration audio ready",
                resolution.audioReady() ? "READY" : "ATTENTION",
                "Narration audio artifact count is " + resolution.audioArtifactCount() + ".",
                "Generate narration audio only through the explicit render action.",
                false
        ));
        checks.add(check(
                "NARRATED_VIDEO_READY",
                "Narrated video ready",
                resolution.videoReady() ? "READY" : "ATTENTION",
                "Narrated video artifact count is " + resolution.narratedVideoArtifactCount() + ".",
                "Generate narrated video only after resolving playback rows.",
                false
        ));
        return List.copyOf(checks);
    }

    private List<NarrationRecoveryHandoffStepVo> steps(
            DemoAcceptanceGateVo gate,
            NarrationPlaybackReviewResolutionVo resolution
    ) {
        List<NarrationRecoveryHandoffStepVo> steps = new ArrayList<>();
        for (DemoAcceptanceGateRunbookStepVo step : gate.runbookSteps()) {
            if ("NARRATION_PLAYBACK_RESOLVED".equals(step.key())) {
                steps.add(new NarrationRecoveryHandoffStepVo(
                        step.key(),
                        step.label(),
                        step.status(),
                        step.primaryAction(),
                        step.safeCommand(),
                        step.safeLink()
                ));
            }
        }
        for (NarrationPlaybackReviewResolutionSegmentVo segment : resolution.unresolvedSegments()) {
            steps.add(new NarrationRecoveryHandoffStepVo(
                    "SEGMENT_" + segment.segmentIndex(),
                    "Resolve segment " + segment.segmentIndex(),
                    segment.resolutionStatus(),
                    segment.nextAction() + " Timing: " + segment.startSeconds() + "s-" + segment.endSeconds() + "s; issues="
                            + (segment.issueCategories().isEmpty() ? "none" : String.join(",", segment.issueCategories())) + ".",
                    "LINGUAFRAME_DEMO_JOB_ID=" + resolution.jobId() + " scripts/demo/narration-playback-review-resolution.sh",
                    "/api/jobs/" + resolution.jobId() + "/narration-playback-review/resolution"
            ));
        }
        return List.copyOf(steps);
    }

    private byte[] zipBytes(
            NarrationRecoveryHandoffVo handoff,
            DemoAcceptanceGateVo gate,
            NarrationPlaybackReviewResolutionVo resolution,
            String markdown
    ) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            writeEntry(zipOutputStream, JSON_ENTRY, handoffJson(handoff));
            writeEntry(zipOutputStream, MARKDOWN_ENTRY, markdown);
            writeEntry(zipOutputStream, ACCEPTANCE_ENTRY, gateJson(gate));
            writeEntry(zipOutputStream, RESOLUTION_ENTRY, resolutionJson(resolution));
            writeEntry(zipOutputStream, README_ENTRY, readme(handoff));
            writeEntry(zipOutputStream, MANIFEST_ENTRY, manifest(handoff));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build narration recovery handoff package", ex);
        }
        return outputStream.toByteArray();
    }

    private String handoffJson(NarrationRecoveryHandoffVo handoff) {
        return """
                {"jobId":"%s","videoId":"%s","status":"%s","phase":"%s","acceptanceGateStatus":"%s","playbackResolutionStatus":"%s","unresolvedSegmentCount":%d,"textRevisionRequiredCount":%d,"rerenderRequiredCount":%d,"unreviewedSegmentCount":%d,"audioReady":%s,"videoReady":%s,"steps":%d,"safeLinks":%d}
                """.formatted(
                json(handoff.jobId()),
                json(handoff.videoId()),
                json(handoff.status()),
                json(handoff.phase()),
                json(handoff.acceptanceGateStatus()),
                json(handoff.playbackResolutionStatus()),
                handoff.unresolvedSegmentCount(),
                handoff.textRevisionRequiredCount(),
                handoff.rerenderRequiredCount(),
                handoff.unreviewedSegmentCount(),
                handoff.audioReady(),
                handoff.videoReady(),
                handoff.steps().size(),
                handoff.safeLinks().size()
        );
    }

    private String gateJson(DemoAcceptanceGateVo gate) {
        return """
                {"jobId":"%s","videoId":"%s","gateStatus":"%s","runbookSteps":%d,"checks":%d}
                """.formatted(json(gate.jobId()), json(gate.videoId()), json(gate.gateStatus()), gate.runbookSteps().size(), gate.checks().size());
    }

    private String resolutionJson(NarrationPlaybackReviewResolutionVo resolution) {
        return """
                {"jobId":"%s","status":"%s","unresolvedSegmentCount":%d,"textRevisionRequiredCount":%d,"rerenderRequiredCount":%d,"unreviewedSegmentCount":%d,"audioReady":%s,"videoReady":%s}
                """.formatted(
                json(resolution.jobId()),
                json(resolution.status()),
                resolution.unresolvedSegmentCount(),
                resolution.textRevisionRequiredCount(),
                resolution.rerenderRequiredCount(),
                resolution.unreviewedSegmentCount(),
                resolution.audioReady(),
                resolution.videoReady()
        );
    }

    private String manifest(NarrationRecoveryHandoffVo handoff) {
        return """
                {"jobId":"%s","status":"%s","entries":["%s","%s","%s","%s","%s","%s"],"safeLinks":%d}
                """.formatted(
                json(handoff.jobId()),
                json(handoff.status()),
                JSON_ENTRY,
                MARKDOWN_ENTRY,
                ACCEPTANCE_ENTRY,
                RESOLUTION_ENTRY,
                README_ENTRY,
                MANIFEST_ENTRY,
                handoff.safeLinks().size()
        );
    }

    private String readme(NarrationRecoveryHandoffVo handoff) {
        return """
                # LinguaFrame Narration Recovery Handoff

                Open `narration-recovery-handoff.md` for the operator recovery checklist.

                Job: %s
                Status: %s

                This package is metadata-only. It does not include media bytes, transcript or subtitle bodies, edited draft bodies, narration scripts, reviewer note bodies, external request or response bodies, object storage keys, local paths, credentials, tokens, or API keys.
                """.formatted(value(handoff.jobId()), value(handoff.status()));
    }

    private List<NarrationRecoveryHandoffLinkVo> safeLinks(String jobId) {
        return List.of(
                link("NARRATION_RECOVERY_HANDOFF_JSON", "Narration recovery handoff", "/api/jobs/" + jobId + "/narration-recovery-handoff", "application/json", "Recovery handoff metadata."),
                link("NARRATION_RECOVERY_HANDOFF_MARKDOWN", "Narration recovery handoff Markdown", "/api/jobs/" + jobId + "/narration-recovery-handoff/markdown/download", "text/markdown", "Recovery checklist Markdown."),
                link("NARRATION_RECOVERY_HANDOFF_ZIP", "Narration recovery handoff ZIP", "/api/jobs/" + jobId + "/narration-recovery-handoff/download", "application/zip", "Offline recovery handoff package."),
                link("ACCEPTANCE_GATE_JSON", "Acceptance gate", "/api/jobs/" + jobId + "/demo-acceptance-gate", "application/json", "Final demo acceptance gate."),
                link("NARRATION_PLAYBACK_RESOLUTION", "Narration playback resolution", "/api/jobs/" + jobId + "/narration-playback-review/resolution", "application/json", "Playback resolution rows and counts."),
                link("NARRATION_PLAYBACK_RESOLUTION_MARKDOWN", "Narration playback resolution Markdown", "/api/jobs/" + jobId + "/narration-playback-review/resolution/markdown/download", "text/markdown", "Playback resolution Markdown.")
        );
    }

    private List<String> packageEntries() {
        return List.of(JSON_ENTRY, MARKDOWN_ENTRY, ACCEPTANCE_ENTRY, RESOLUTION_ENTRY, README_ENTRY, MANIFEST_ENTRY);
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration recovery handoff is metadata-only.",
                "Narration text and reviewer note bodies are excluded from JSON, Markdown, and ZIP outputs.",
                "External model calls, FFmpeg work, upload APIs, and artifact generation remain behind existing explicit operator actions.",
                "Use this package to decide which narration rows to resolve before re-running playback resolution and acceptance gate."
        );
    }

    private String status(boolean narrationBlocked, NarrationPlaybackReviewResolutionVo resolution) {
        if (narrationBlocked) {
            return "BLOCKED";
        }
        if ("READY".equals(resolution.status()) || resolution.segmentCount() == 0) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String phase(String status) {
        return switch (status) {
            case "READY" -> "NARRATION_RECOVERY_READY";
            case "ATTENTION" -> "NARRATION_RECOVERY_NEEDS_REVIEW";
            default -> "NARRATION_RECOVERY_BLOCKED";
        };
    }

    private String headline(String status) {
        return switch (status) {
            case "READY" -> "Narration recovery is clear for final handoff.";
            case "ATTENTION" -> "Narration recovery has evidence to review before final handoff.";
            default -> "Narration recovery is blocked by unresolved playback rows.";
        };
    }

    private String recommendedNextAction(String status) {
        return switch (status) {
            case "READY" -> "Continue with final handoff or demo portal export.";
            case "ATTENTION" -> "Review narration recovery evidence, then re-run playback resolution and acceptance gate.";
            default -> "Open playback resolution, focus unresolved narration rows, save revisions, regenerate narration media, then re-run acceptance gate.";
        };
    }

    private String statusFromGate(String gateStatus) {
        return switch (gateStatus) {
            case "READY" -> "READY";
            case "BLOCKED" -> "BLOCKED";
            default -> "ATTENTION";
        };
    }

    private String statusFromResolution(String resolutionStatus) {
        return switch (resolutionStatus) {
            case "READY" -> "READY";
            case "BLOCKED" -> "BLOCKED";
            default -> "ATTENTION";
        };
    }

    private static NarrationRecoveryHandoffCheckVo check(
            String key,
            String label,
            String status,
            String detail,
            String nextAction,
            boolean required
    ) {
        return new NarrationRecoveryHandoffCheckVo(key, label, status, detail, nextAction, required);
    }

    private static NarrationRecoveryHandoffLinkVo link(String kind, String label, String href, String contentType, String description) {
        return new NarrationRecoveryHandoffLinkVo(kind, label, href, contentType, description);
    }

    private static void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static String value(Object value) {
        return value == null ? "N/A" : String.valueOf(value);
    }

    private static String json(Object value) {
        return value(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
