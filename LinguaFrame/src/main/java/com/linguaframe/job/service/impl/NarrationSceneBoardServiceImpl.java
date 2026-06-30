package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.NarrationSceneBoardActionVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardCheckVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardLinkVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardSegmentVo;
import com.linguaframe.job.domain.vo.NarrationSceneBoardVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.repository.NarrationMixKeyframeRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.NarrationSceneBoardService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NarrationSceneBoardServiceImpl implements NarrationSceneBoardService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal DENSE_CHARACTERS_PER_SECOND = new BigDecimal("18.000");

    private final NarrationSegmentRepository segmentRepository;
    private final NarrationMixKeyframeRepository mixKeyframeRepository;
    private final JobArtifactRepository artifactRepository;
    private final NarrationVoiceCatalogService voiceCatalogService;
    private final Clock clock;

    public NarrationSceneBoardServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationMixKeyframeRepository mixKeyframeRepository,
            JobArtifactRepository artifactRepository,
            NarrationVoiceCatalogService voiceCatalogService,
            Clock clock
    ) {
        this.segmentRepository = segmentRepository;
        this.mixKeyframeRepository = mixKeyframeRepository;
        this.artifactRepository = artifactRepository;
        this.voiceCatalogService = voiceCatalogService;
        this.clock = clock;
    }

    @Override
    public NarrationSceneBoardVo getSceneBoard(String jobId) {
        List<NarrationSegmentRecord> records = segmentRepository.findByJobId(jobId).stream()
                .sorted(Comparator.comparingInt(NarrationSegmentRecord::segmentIndex))
                .toList();
        List<JobArtifactRecord> artifacts = artifactRepository.findByJobId(jobId);
        boolean audioReady = hasArtifact(artifacts, JobArtifactType.NARRATION_AUDIO);
        boolean videoReady = hasArtifact(artifacts, JobArtifactType.NARRATED_VIDEO);
        int keyframeCount = mixKeyframeRepository.findByJobId(jobId).size();
        TimelineSummary timeline = timelineSummary(records);
        List<NarrationSceneBoardSegmentVo> segments = records.stream()
                .map(this::toSegment)
                .toList();
        int mixOverrideCount = (int) records.stream().filter(this::hasMixOverride).count();
        int voiceCount = voiceCount(records);
        List<NarrationSceneBoardCheckVo> checks = checks(records, timeline, audioReady, videoReady);
        String status = status(records, checks);
        return new NarrationSceneBoardVo(
                jobId,
                clock.instant(),
                status,
                records.size(),
                timeline.coveredSeconds(),
                timeline.totalSpanSeconds(),
                coveragePercent(timeline),
                timeline.gapCount(),
                timeline.gapSeconds(),
                timeline.hasOverlap(),
                voiceCount,
                mixOverrideCount,
                keyframeCount,
                audioReady,
                videoReady,
                segments,
                checks,
                recommendedActions(status, audioReady, videoReady),
                safeLinks(jobId),
                List.of(
                        "Scene board reports timing, voice, mix, and artifact metadata only.",
                        "Terminal and Markdown exports intentionally exclude narration script text.",
                        "Scene-board actions reuse existing workspace save, preview, preflight, and render controls."
                )
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationSceneBoardVo board = getSceneBoard(jobId);
        StringBuilder markdown = new StringBuilder();
        markdown.append("# Narration Scene Board\n\n");
        markdown.append("- Job: ").append(board.jobId()).append('\n');
        markdown.append("- Status: ").append(board.status()).append('\n');
        markdown.append("- Segments: ").append(board.segmentCount()).append('\n');
        markdown.append("- Coverage: ").append(board.coveragePercent()).append("%\n");
        markdown.append("- Gaps: ").append(board.gapCount()).append(" (").append(board.gapSeconds()).append("s)\n");
        markdown.append("- Overlap: ").append(board.hasOverlap()).append('\n');
        markdown.append("- Audio ready: ").append(board.audioReady()).append('\n');
        markdown.append("- Video ready: ").append(board.videoReady()).append("\n\n");
        markdown.append("## Segments\n\n");
        if (board.segments().isEmpty()) {
            markdown.append("- No narration segments saved.\n");
        } else {
            for (NarrationSceneBoardSegmentVo segment : board.segments()) {
                markdown.append("- ")
                        .append(segment.index() + 1)
                        .append(". ")
                        .append(segment.windowLabel())
                        .append(" | voice=")
                        .append(segment.voiceState())
                        .append(" | chars=")
                        .append(segment.characterCount())
                        .append(" | density=")
                        .append(segment.readingDensity())
                        .append(" | timing=")
                        .append(segment.timingStatus())
                        .append(" | mix=")
                        .append(segment.mixState())
                        .append(" | readiness=")
                        .append(segment.readiness())
                        .append('\n');
            }
        }
        markdown.append("\n## Checks\n\n");
        for (NarrationSceneBoardCheckVo check : board.checks()) {
            markdown.append("- ").append(check.label()).append(": ").append(check.status()).append(" - ").append(check.detail()).append('\n');
        }
        markdown.append("\n## Recommended Actions\n\n");
        for (NarrationSceneBoardActionVo action : board.recommendedActions()) {
            markdown.append("- ").append(action.label()).append(": ").append(action.detail()).append(" (").append(action.target()).append(")\n");
        }
        return markdown.toString();
    }

    private NarrationSceneBoardSegmentVo toSegment(NarrationSegmentRecord record) {
        BigDecimal start = normalize(record.startSeconds());
        BigDecimal end = normalize(record.endSeconds());
        BigDecimal duration = end.subtract(start).setScale(3, RoundingMode.HALF_UP);
        int characterCount = record.text() == null ? 0 : record.text().length();
        BigDecimal readingDensity = duration.compareTo(ZERO) <= 0
                ? ZERO
                : new BigDecimal(characterCount).divide(duration, 3, RoundingMode.HALF_UP);
        String timingStatus = duration.compareTo(ZERO) <= 0 ? "INVALID_RANGE" : "READY";
        String mixState = hasMixOverride(record) ? "OVERRIDE" : "INHERIT";
        String readiness = segmentReadiness(record, duration, readingDensity);
        return new NarrationSceneBoardSegmentVo(
                record.segmentIndex(),
                start,
                end,
                duration,
                formatSeconds(start) + "-" + formatSeconds(end),
                record.voice() == null || record.voice().isBlank() ? "Inherit default" : record.voice().trim(),
                characterCount,
                readingDensity,
                timingStatus,
                mixState,
                readiness
        );
    }

    private String segmentReadiness(NarrationSegmentRecord record, BigDecimal duration, BigDecimal readingDensity) {
        if (duration.compareTo(ZERO) <= 0 || record.text() == null || record.text().isBlank()) {
            return "BLOCKED";
        }
        if (!voiceCatalogService.containsVoice(record.voice()) || readingDensity.compareTo(DENSE_CHARACTERS_PER_SECOND) > 0) {
            return "ATTENTION";
        }
        return "READY";
    }

    private List<NarrationSceneBoardCheckVo> checks(
            List<NarrationSegmentRecord> records,
            TimelineSummary timeline,
            boolean audioReady,
            boolean videoReady
    ) {
        boolean hasBlank = records.stream().anyMatch(record -> record.text() == null || record.text().isBlank());
        boolean invalidWindow = records.stream().anyMatch(record -> normalize(record.endSeconds()).compareTo(normalize(record.startSeconds())) <= 0);
        boolean unknownVoice = records.stream().anyMatch(record -> !voiceCatalogService.containsVoice(record.voice()));
        boolean denseText = records.stream()
                .map(this::toSegment)
                .anyMatch(segment -> segment.readingDensity().compareTo(DENSE_CHARACTERS_PER_SECOND) > 0);
        return List.of(
                new NarrationSceneBoardCheckVo("segments", "Saved narration rows", records.isEmpty() ? "BLOCKED" : "READY", records.isEmpty() ? "No saved narration rows." : records.size() + " saved narration row(s)."),
                new NarrationSceneBoardCheckVo("text", "Narration text", hasBlank ? "BLOCKED" : "READY", hasBlank ? "At least one row has blank text." : "All saved rows include text."),
                new NarrationSceneBoardCheckVo("timing", "Timing windows", invalidWindow || timeline.hasOverlap() ? "BLOCKED" : "READY", invalidWindow ? "At least one row has an invalid window." : timeline.hasOverlap() ? "Saved rows overlap." : "Saved timing windows are valid."),
                new NarrationSceneBoardCheckVo("voice", "Voice presets", unknownVoice ? "BLOCKED" : "READY", unknownVoice ? "At least one row uses an unknown voice preset." : "All explicit voices are configured presets."),
                new NarrationSceneBoardCheckVo("density", "Reading density", denseText ? "ATTENTION" : "READY", denseText ? "At least one row may be too dense for comfortable narration." : "Saved rows have acceptable text density."),
                new NarrationSceneBoardCheckVo("audio", "Narration audio", audioReady ? "READY" : "ATTENTION", audioReady ? "Narration audio artifact exists." : "Generate narration audio after saving the final draft."),
                new NarrationSceneBoardCheckVo("video", "Narrated video", videoReady ? "READY" : "ATTENTION", videoReady ? "Narrated video artifact exists." : "Generate narrated video when the demo needs playable output.")
        );
    }

    private String status(List<NarrationSegmentRecord> records, List<NarrationSceneBoardCheckVo> checks) {
        if (records.isEmpty()) {
            return "EMPTY";
        }
        if (checks.stream().anyMatch(check -> "BLOCKED".equals(check.status()))) {
            return "BLOCKED";
        }
        if (checks.stream().anyMatch(check -> "ATTENTION".equals(check.status()))) {
            return "ATTENTION";
        }
        return "READY";
    }

    private List<NarrationSceneBoardActionVo> recommendedActions(String status, boolean audioReady, boolean videoReady) {
        if ("EMPTY".equals(status)) {
            return List.of(new NarrationSceneBoardActionVo("add-rows", "Add timed narration rows", "Paste quick script rows or add rows in the browser workspace.", "#narration-workspace"));
        }
        if ("BLOCKED".equals(status)) {
            return List.of(new NarrationSceneBoardActionVo("fix-draft", "Fix blocked narration rows", "Focus blocked timing, text, or voice rows before saving.", "#narration-workspace"));
        }
        if (!audioReady) {
            return List.of(new NarrationSceneBoardActionVo("save-generate-audio", "Save and generate narration audio", "Save the final draft, then generate narration audio.", "#narration-workspace"));
        }
        if (!videoReady) {
            return List.of(new NarrationSceneBoardActionVo("render-preflight", "Run render preflight", "Check provider and media readiness before narrated-video render.", "#narration-workspace"));
        }
        return List.of(
                new NarrationSceneBoardActionVo("render-preflight", "Run render preflight", "Re-check provider and media readiness before presenting or rerendering.", "#narration-workspace"),
                new NarrationSceneBoardActionVo("review-delivery", "Review narrated delivery", "Use render review, playback review, and delivery package before demo handoff.", "#narration-workspace")
        );
    }

    private List<NarrationSceneBoardLinkVo> safeLinks(String jobId) {
        return List.of(
                new NarrationSceneBoardLinkVo("workspace", "/api/jobs/" + jobId + "/narration-workspace", "Narration workspace"),
                new NarrationSceneBoardLinkVo("scene-board-markdown", "/api/jobs/" + jobId + "/narration-scene-board/markdown/download", "Scene board Markdown"),
                new NarrationSceneBoardLinkVo("evidence", "/api/jobs/" + jobId + "/narration-evidence", "Narration evidence"),
                new NarrationSceneBoardLinkVo("script-package", "/api/jobs/" + jobId + "/narration-script-package", "Narration script package"),
                new NarrationSceneBoardLinkVo("render-review", "/api/jobs/" + jobId + "/narration-render-review", "Narration render review"),
                new NarrationSceneBoardLinkVo("playback-review", "/api/jobs/" + jobId + "/narration-playback-review", "Narration playback review"),
                new NarrationSceneBoardLinkVo("delivery-package", "/api/jobs/" + jobId + "/narration-delivery-package", "Narration delivery package")
        );
    }

    private TimelineSummary timelineSummary(List<NarrationSegmentRecord> records) {
        if (records.isEmpty()) {
            return new TimelineSummary(ZERO, ZERO, ZERO, 0, false);
        }
        List<NarrationSegmentRecord> byTime = records.stream()
                .sorted(Comparator.comparing(NarrationSegmentRecord::startSeconds))
                .toList();
        BigDecimal start = normalize(byTime.get(0).startSeconds());
        BigDecimal end = byTime.stream()
                .map(record -> normalize(record.endSeconds()))
                .max(BigDecimal::compareTo)
                .orElse(start);
        BigDecimal covered = records.stream()
                .map(record -> normalize(record.endSeconds()).subtract(normalize(record.startSeconds())))
                .reduce(ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
        BigDecimal gapSeconds = ZERO;
        int gapCount = 0;
        boolean hasOverlap = false;
        for (int index = 1; index < byTime.size(); index += 1) {
            BigDecimal previousEnd = normalize(byTime.get(index - 1).endSeconds());
            BigDecimal currentStart = normalize(byTime.get(index).startSeconds());
            int comparison = currentStart.compareTo(previousEnd);
            if (comparison > 0) {
                gapCount += 1;
                gapSeconds = gapSeconds.add(currentStart.subtract(previousEnd));
            } else if (comparison < 0) {
                hasOverlap = true;
            }
        }
        return new TimelineSummary(
                covered,
                end.subtract(start).setScale(3, RoundingMode.HALF_UP),
                gapSeconds.setScale(3, RoundingMode.HALF_UP),
                gapCount,
                hasOverlap
        );
    }

    private BigDecimal coveragePercent(TimelineSummary timeline) {
        if (timeline.totalSpanSeconds().compareTo(ZERO) <= 0) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return timeline.coveredSeconds()
                .multiply(ONE_HUNDRED)
                .divide(timeline.totalSpanSeconds(), 2, RoundingMode.HALF_UP);
    }

    private int voiceCount(List<NarrationSegmentRecord> records) {
        Set<String> voices = new LinkedHashSet<>();
        for (NarrationSegmentRecord record : records) {
            if (record.voice() != null && !record.voice().isBlank()) {
                voices.add(record.voice().trim());
            }
        }
        return voices.size();
    }

    private boolean hasArtifact(List<JobArtifactRecord> artifacts, JobArtifactType type) {
        return artifacts.stream().anyMatch(artifact -> artifact.type() == type);
    }

    private boolean hasMixOverride(NarrationSegmentRecord record) {
        return record.duckingVolume() != null || record.narrationVolume() != null || record.fadeDurationMs() != null;
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? ZERO : value.setScale(3, RoundingMode.HALF_UP);
    }

    private String formatSeconds(BigDecimal seconds) {
        BigDecimal normalized = normalize(seconds);
        long wholeSeconds = normalized.longValue();
        int minutes = (int) (wholeSeconds / 60);
        BigDecimal secondsPart = normalized.subtract(new BigDecimal(minutes * 60L)).setScale(3, RoundingMode.HALF_UP);
        return String.format("%02d:%06.3f", minutes, secondsPart);
    }

    private record TimelineSummary(
            BigDecimal coveredSeconds,
            BigDecimal totalSpanSeconds,
            BigDecimal gapSeconds,
            int gapCount,
            boolean hasOverlap
    ) {
    }
}
