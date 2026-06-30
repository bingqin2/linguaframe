package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewCheckVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewLinkVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewMetricVo;
import com.linguaframe.job.domain.vo.NarrationRenderReviewVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.service.JobArtifactService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationRenderReviewService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NarrationRenderReviewServiceImpl implements NarrationRenderReviewService {

    private final NarrationWorkspaceService workspaceService;
    private final NarrationEvidenceService evidenceService;
    private final JobArtifactService artifactService;

    public NarrationRenderReviewServiceImpl(
            NarrationWorkspaceService workspaceService,
            NarrationEvidenceService evidenceService,
            JobArtifactService artifactService
    ) {
        this.workspaceService = workspaceService;
        this.evidenceService = evidenceService;
        this.artifactService = artifactService;
    }

    @Override
    public NarrationRenderReviewVo getReview(String jobId) {
        NarrationWorkspaceVo workspace = workspaceService.getWorkspace(jobId);
        NarrationEvidenceVo evidence = evidenceService.getEvidence(jobId);
        List<JobArtifactVo> artifacts = artifactService.listArtifacts(jobId);
        int audioArtifactCount = countArtifacts(artifacts, JobArtifactType.NARRATION_AUDIO);
        int videoArtifactCount = countArtifacts(artifacts, JobArtifactType.NARRATED_VIDEO);
        Optional<JobArtifactVo> waveformArtifact = latestArtifact(artifacts, JobArtifactType.NARRATION_WAVEFORM);
        boolean audioReady = evidence.narrationAudioReady() || audioArtifactCount > 0;
        boolean videoReady = evidence.narratedVideoReady() || videoArtifactCount > 0;
        boolean waveformReady = waveformArtifact.isPresent();
        boolean hasOverlap = workspace.timeline() != null && workspace.timeline().hasOverlap();
        String status = status(workspace.segmentCount(), hasOverlap, audioReady, videoReady, waveformReady);
        String nextAction = nextAction(status, workspace.segmentCount(), hasOverlap, audioReady, videoReady);
        return new NarrationRenderReviewVo(
                jobId,
                status,
                nextAction,
                workspace.segmentCount(),
                workspace.totalDurationSeconds(),
                workspace.timeline() == null ? BigDecimal.ZERO : workspace.timeline().totalSpanSeconds(),
                workspace.timeline() == null ? 0 : workspace.timeline().gapCount(),
                workspace.timeline() == null ? BigDecimal.ZERO : workspace.timeline().gapSeconds(),
                hasOverlap,
                evidence.voiceSummary(),
                evidence.segmentMixOverrideCount(),
                evidence.segmentMixOverrideSummary(),
                mixKeyframeCount(workspace, evidence),
                mixKeyframeLaneSummary(evidence),
                audioReady,
                Math.max(evidence.audioArtifactCount(), audioArtifactCount),
                videoReady,
                Math.max(evidence.narratedVideoArtifactCount(), videoArtifactCount),
                waveformReady,
                waveformArtifact.map(JobArtifactVo::artifactId).orElse(""),
                false,
                metrics(workspace, evidence, audioArtifactCount, videoArtifactCount, waveformReady),
                checks(workspace.segmentCount(), hasOverlap, audioReady, videoReady, waveformReady),
                safeLinks(jobId),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationRenderReviewVo review = getReview(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# Narration Render Review");
        lines.add("");
        lines.add("- Job: " + review.jobId());
        lines.add("- Status: " + review.status());
        lines.add("- Next action: " + review.nextAction());
        lines.add("- Segment count: " + review.segmentCount());
        lines.add("- Total narration duration seconds: " + review.totalNarrationDurationSeconds());
        lines.add("- Covered span seconds: " + review.coveredSpanSeconds());
        lines.add("- Gap count: " + review.gapCount());
        lines.add("- Gap seconds: " + review.gapSeconds());
        lines.add("- Timeline has overlap: " + review.timelineHasOverlap());
        lines.add("- Voice summary: " + review.voiceSummary());
        lines.add("- Segment mix override count: " + review.segmentMixOverrideCount());
        lines.add("- Segment mix override summary: " + review.segmentMixOverrideSummary());
        lines.add("- Mix keyframe count: " + review.mixKeyframeCount());
        lines.add("- Mix keyframe lane summary: " + review.mixKeyframeLaneSummary());
        lines.add("- Narration audio ready: " + review.audioReady());
        lines.add("- Narrated video ready: " + review.videoReady());
        lines.add("- Waveform ready: " + review.waveformReady());
        lines.add("- Waveform artifact: " + valueOrDefault(review.waveformArtifactId(), "N/A"));
        lines.add("");
        lines.add("## Checks");
        for (NarrationRenderReviewCheckVo check : review.checks()) {
            lines.add("- " + check.label() + ": " + check.status() + " - " + check.detail());
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationRenderReviewLinkVo link : review.safeLinks()) {
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

    private String status(int segmentCount, boolean hasOverlap, boolean audioReady, boolean videoReady, boolean waveformReady) {
        if (segmentCount <= 0 || hasOverlap) {
            return "BLOCKED";
        }
        if (audioReady && videoReady && waveformReady) {
            return "READY";
        }
        return "ATTENTION";
    }

    private String nextAction(String status, int segmentCount, boolean hasOverlap, boolean audioReady, boolean videoReady) {
        if (segmentCount <= 0) {
            return "Add narration segments before rendering.";
        }
        if (hasOverlap) {
            return "Resolve overlapping narration windows before review.";
        }
        if (!audioReady) {
            return "Generate narration audio before review.";
        }
        if (!videoReady) {
            return "Generate narrated video or keep audio-only review.";
        }
        return "Review narrated video and export handoff evidence.";
    }

    private List<NarrationRenderReviewMetricVo> metrics(
            NarrationWorkspaceVo workspace,
            NarrationEvidenceVo evidence,
            int audioArtifactCount,
            int videoArtifactCount,
            boolean waveformReady
    ) {
        return List.of(
                metric("segments", "Segments", workspace.segmentCount()),
                metric("durationSeconds", "Narration duration", workspace.totalDurationSeconds()),
                metric("gapCount", "Gaps", workspace.timeline() == null ? 0 : workspace.timeline().gapCount()),
                metric("audioArtifacts", "Narration audio artifacts", Math.max(evidence.audioArtifactCount(), audioArtifactCount)),
                metric("narratedVideoArtifacts", "Narrated video artifacts", Math.max(evidence.narratedVideoArtifactCount(), videoArtifactCount)),
                metric("waveform", "Waveform", waveformReady ? "READY" : "MISSING"),
                metric("keyframes", "Mix keyframes", mixKeyframeCount(workspace, evidence))
        );
    }

    private List<NarrationRenderReviewCheckVo> checks(
            int segmentCount,
            boolean hasOverlap,
            boolean audioReady,
            boolean videoReady,
            boolean waveformReady
    ) {
        return List.of(
                check("SEGMENTS", "Segments", segmentCount > 0 ? "PASS" : "BLOCK", segmentCount > 0 ? segmentCount + " narration windows saved." : "No saved narration windows."),
                check("TIMELINE_OVERLAP", "Timeline overlap", hasOverlap ? "BLOCK" : "PASS", hasOverlap ? "Resolve overlapping windows." : "No overlap reported."),
                check("NARRATION_AUDIO", "Narration audio", audioReady ? "PASS" : "BLOCK", audioReady ? "Narration audio artifact is available." : "Generate narration audio."),
                check("NARRATED_VIDEO", "Narrated video", videoReady ? "PASS" : "WARN", videoReady ? "Narrated video artifact is available." : "Audio-only review is possible."),
                check("WAVEFORM", "Waveform evidence", waveformReady ? "PASS" : "WARN", waveformReady ? "Persistent waveform artifact is available." : "Waveform evidence is missing or fallback-only.")
        );
    }

    private List<NarrationRenderReviewLinkVo> safeLinks(String jobId) {
        return List.of(
                link("NARRATION_RENDER_REVIEW", "Narration render review JSON", "/api/jobs/" + jobId + "/narration-render-review", "application/json"),
                link("NARRATION_RENDER_REVIEW_MARKDOWN", "Narration render review Markdown", "/api/jobs/" + jobId + "/narration-render-review/markdown/download", "text/markdown"),
                link("NARRATION_EVIDENCE", "Narration evidence", "/api/jobs/" + jobId + "/narration-evidence", "application/json"),
                link("NARRATION_WAVEFORM", "Narration waveform", "/api/jobs/" + jobId + "/narration-waveform", "application/json")
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration render review is metadata-only and excludes narration text bodies, transcript text, subtitle text, object keys, local paths, provider payloads, tokens, API keys, and media bytes.",
                "This review does not call OpenAI, TTS providers, FFmpeg render, or mutate narration rows."
        );
    }

    private int countArtifacts(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return Math.toIntExact(artifacts.stream().filter(artifact -> artifact.type() == type).count());
    }

    private Optional<JobArtifactVo> latestArtifact(List<JobArtifactVo> artifacts, JobArtifactType type) {
        return artifacts.stream()
                .filter(artifact -> artifact.type() == type)
                .findFirst();
    }

    private int mixKeyframeCount(NarrationWorkspaceVo workspace, NarrationEvidenceVo evidence) {
        if (workspace.mixAutomation() != null && workspace.mixAutomation().keyframeCount() > 0) {
            return workspace.mixAutomation().keyframeCount();
        }
        return evidence.mixKeyframeCount();
    }

    private String mixKeyframeLaneSummary(NarrationEvidenceVo evidence) {
        return blank(evidence.mixKeyframeLaneSummary()) ? "none" : evidence.mixKeyframeLaneSummary();
    }

    private NarrationRenderReviewMetricVo metric(String key, String label, Object value) {
        return new NarrationRenderReviewMetricVo(key, label, String.valueOf(value));
    }

    private NarrationRenderReviewCheckVo check(String key, String label, String status, String detail) {
        return new NarrationRenderReviewCheckVo(key, label, status, detail);
    }

    private NarrationRenderReviewLinkVo link(String kind, String label, String href, String contentType) {
        return new NarrationRenderReviewLinkVo(kind, label, href, contentType);
    }

    private String valueOrDefault(String value, String fallback) {
        return blank(value) ? fallback : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
