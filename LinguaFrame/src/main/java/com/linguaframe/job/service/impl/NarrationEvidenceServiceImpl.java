package com.linguaframe.job.service.impl;

import com.linguaframe.job.domain.bo.StoredNarrationEvidencePackageBo;
import com.linguaframe.job.domain.entity.NarrationMixKeyframeRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.NarrationMixLane;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceCheckVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceLinkVo;
import com.linguaframe.job.domain.vo.NarrationEvidenceVo;
import com.linguaframe.job.repository.NarrationMixKeyframeRepository;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationEvidenceService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NarrationEvidenceServiceImpl implements NarrationEvidenceService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final String TIMED_AUDIO_BED = "TIMED_AUDIO_BED";
    private static final String DUCKED_ORIGINAL_AUDIO = "DUCKED_ORIGINAL_AUDIO";
    private static final String MISSING = "MISSING";

    private final NarrationSegmentRepository narrationSegmentRepository;
    private final LocalizationJobQueryService queryService;
    private final NarrationMixSettingsRepository mixSettingsRepository;
    private final NarrationMixKeyframeRepository mixKeyframeRepository;
    private final NarrationVoiceCatalogService voiceCatalogService;

    public NarrationEvidenceServiceImpl(
            NarrationSegmentRepository narrationSegmentRepository,
            LocalizationJobQueryService queryService,
            NarrationMixSettingsRepository mixSettingsRepository
    ) {
        this(
                narrationSegmentRepository,
                queryService,
                mixSettingsRepository,
                new EmptyNarrationMixKeyframeRepository(),
                () -> new com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo(
                        "demo",
                        "demo-voice",
                        List.of(new com.linguaframe.job.domain.vo.NarrationVoicePresetVo("demo-voice", "Demo voice", "demo", true, "Deterministic local demo TTS voice.")),
                        List.of()
                )
        );
    }

    public NarrationEvidenceServiceImpl(
            NarrationSegmentRepository narrationSegmentRepository,
            LocalizationJobQueryService queryService,
            NarrationMixSettingsRepository mixSettingsRepository,
            NarrationMixKeyframeRepository mixKeyframeRepository
    ) {
        this(
                narrationSegmentRepository,
                queryService,
                mixSettingsRepository,
                mixKeyframeRepository,
                () -> new com.linguaframe.job.domain.vo.NarrationVoiceCatalogVo(
                        "demo",
                        "demo-voice",
                        List.of(new com.linguaframe.job.domain.vo.NarrationVoicePresetVo("demo-voice", "Demo voice", "demo", true, "Deterministic local demo TTS voice.")),
                        List.of()
                )
        );
    }

    @Autowired
    public NarrationEvidenceServiceImpl(
            NarrationSegmentRepository narrationSegmentRepository,
            LocalizationJobQueryService queryService,
            NarrationMixSettingsRepository mixSettingsRepository,
            NarrationMixKeyframeRepository mixKeyframeRepository,
            NarrationVoiceCatalogService voiceCatalogService
    ) {
        this.narrationSegmentRepository = narrationSegmentRepository;
        this.queryService = queryService;
        this.mixSettingsRepository = mixSettingsRepository;
        this.mixKeyframeRepository = mixKeyframeRepository;
        this.voiceCatalogService = voiceCatalogService;
    }

    @Override
    public NarrationEvidenceVo getEvidence(String jobId) {
        List<NarrationSegmentRecord> segments = narrationSegmentRepository.findByJobId(jobId).stream()
                .sorted(Comparator.comparingInt(NarrationSegmentRecord::segmentIndex))
                .toList();
        JobDiagnosticsReportVo report = queryService.getDiagnosticsReport(jobId);
        long audioArtifacts = report.artifacts().stream()
                .filter(artifact -> artifact.type() == JobArtifactType.NARRATION_AUDIO)
                .count();
        long narratedVideoArtifacts = report.artifacts().stream()
                .filter(artifact -> artifact.type() == JobArtifactType.NARRATED_VIDEO)
                .count();
        String status = status(segments.size(), audioArtifacts, narratedVideoArtifacts);
        NarrationMixSettingsSupport.ResolvedNarrationMixSettings mixSettings =
                narratedVideoArtifacts > 0 ? NarrationMixSettingsSupport.resolve(mixSettingsRepository, jobId) : null;
        List<NarrationMixKeyframeRecord> keyframes = mixKeyframeRepository.findByJobId(jobId);
        return new NarrationEvidenceVo(
                jobId,
                status,
                segments.size(),
                totalCharacters(segments),
                totalTimelineDurationSeconds(segments),
                timelineGapCount(segments),
                timelineGapSeconds(segments),
                timelineHasOverlap(segments),
                voicePresetCount(segments),
                voiceSummary(segments),
                voiceCatalogService.defaultVoice(),
                audioArtifacts > 0,
                Math.toIntExact(audioArtifacts),
                audioArtifacts > 0 ? TIMED_AUDIO_BED : MISSING,
                audioArtifacts > 0,
                narratedVideoArtifacts > 0,
                Math.toIntExact(narratedVideoArtifacts),
                narratedVideoArtifacts > 0 ? DUCKED_ORIGINAL_AUDIO : MISSING,
                mixSettings == null ? null : mixSettings.duckingVolume(),
                mixSettings == null ? null : mixSettings.narrationVolume(),
                mixSettings == null ? 0 : mixSettings.fadeDurationMs(),
                mixSettings == null ? null : mixSettings.source(),
                segmentMixOverrideCount(segments),
                segmentMixOverrideSummary(segments),
                keyframes.size(),
                mixKeyframeLaneSummary(keyframes),
                checks(segments.size(), audioArtifacts, narratedVideoArtifacts),
                safeLinks(jobId),
                packageEntries(jobId),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationEvidenceVo evidence = getEvidence(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# Narration Evidence");
        lines.add("");
        lines.add("- Job: " + evidence.jobId());
        lines.add("- Status: " + evidence.status());
        lines.add("- Segment count: " + evidence.segmentCount());
        lines.add("- Total narration characters: " + evidence.totalCharacterCount());
        lines.add("- Total timeline duration seconds: " + evidence.totalTimelineDurationSeconds());
        lines.add("- Timeline gap count: " + evidence.timelineGapCount());
        lines.add("- Timeline gap seconds: " + evidence.timelineGapSeconds());
        lines.add("- Timeline has overlap: " + evidence.timelineHasOverlap());
        lines.add("- Voice preset count: " + evidence.voicePresetCount());
        lines.add("- Voice summary: " + evidence.voiceSummary());
        lines.add("- Default voice: " + evidence.defaultVoice());
        lines.add("- Narration audio artifacts: " + evidence.audioArtifactCount());
        lines.add("- Audio layout: " + evidence.audioLayout());
        lines.add("- Time aligned: " + evidence.timeAligned());
        lines.add("- Narrated video artifacts: " + evidence.narratedVideoArtifactCount());
        lines.add("- Mix mode: " + evidence.mixMode());
        lines.add("- Ducking volume: " + valueOrDefault(evidence.duckingVolume(), "N/A"));
        lines.add("- Narration volume: " + valueOrDefault(evidence.narrationVolume(), "N/A"));
        lines.add("- Fade duration ms: " + (evidence.fadeDurationMs() == 0 ? "N/A" : evidence.fadeDurationMs()));
        lines.add("- Mix settings source: " + valueOrDefault(evidence.mixSettingsSource(), "N/A"));
        lines.add("- Segment mix override count: " + evidence.segmentMixOverrideCount());
        lines.add("- Segment mix override summary: " + evidence.segmentMixOverrideSummary());
        lines.add("- Mix keyframe count: " + evidence.mixKeyframeCount());
        lines.add("- Mix keyframe lane summary: " + evidence.mixKeyframeLaneSummary());
        lines.add("");
        lines.add("## Checks");
        for (NarrationEvidenceCheckVo check : evidence.checks()) {
            lines.add("- " + check.label() + ": " + check.status() + " - " + check.detail());
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationEvidenceLinkVo link : evidence.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Package Entries");
        for (String entry : evidence.packageEntries()) {
            lines.add("- " + entry);
        }
        lines.add("");
        lines.add("## Safety Notes");
        for (String note : evidence.safetyNotes()) {
            lines.add("- " + note);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    @Override
    public StoredNarrationEvidencePackageBo openPackage(String jobId) {
        NarrationEvidenceVo evidence = getEvidence(jobId);
        String markdown = renderMarkdown(jobId);
        byte[] content = zipBytes(evidence, markdown);
        return new StoredNarrationEvidencePackageBo(
                "linguaframe-job-" + jobId + "-narration-evidence.zip",
                "application/zip",
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private byte[] zipBytes(NarrationEvidenceVo evidence, String markdown) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "manifest.json", manifest(evidence));
            writeEntry(zipOutputStream, "narration-evidence.md", markdown);
            writeEntry(zipOutputStream, "narration-summary.json", summary(evidence));
            writeEntry(zipOutputStream, "README.md", readme(evidence));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build narration evidence package", ex);
        }
        return outputStream.toByteArray();
    }

    private String manifest(NarrationEvidenceVo evidence) {
        return """
                {"jobId":"%s","status":"%s","segmentCount":%d,"timelineGapCount":%d,"timelineGapSeconds":"%s","timelineHasOverlap":%s,"voicePresetCount":%d,"voiceSummary":"%s","defaultVoice":"%s","narrationAudioReady":%s,"audioLayout":"%s","timeAligned":%s,"narratedVideoReady":%s,"mixMode":"%s","duckingVolume":%s,"narrationVolume":%s,"fadeDurationMs":%d,"mixSettingsSource":"%s","segmentMixOverrideCount":%d,"segmentMixOverrideSummary":"%s","mixKeyframeCount":%d,"mixKeyframeLaneSummary":"%s","includesNarrationTextBodies":false}
                """.formatted(
                json(evidence.jobId()),
                json(evidence.status()),
                evidence.segmentCount(),
                evidence.timelineGapCount(),
                evidence.timelineGapSeconds(),
                evidence.timelineHasOverlap(),
                evidence.voicePresetCount(),
                json(evidence.voiceSummary()),
                json(evidence.defaultVoice()),
                evidence.narrationAudioReady(),
                json(evidence.audioLayout()),
                evidence.timeAligned(),
                evidence.narratedVideoReady(),
                json(evidence.mixMode()),
                jsonNumberOrNull(evidence.duckingVolume()),
                jsonNumberOrNull(evidence.narrationVolume()),
                evidence.fadeDurationMs(),
                json(valueOrDefault(evidence.mixSettingsSource(), "N/A")),
                evidence.segmentMixOverrideCount(),
                json(evidence.segmentMixOverrideSummary()),
                evidence.mixKeyframeCount(),
                json(evidence.mixKeyframeLaneSummary())
        );
    }

    private String summary(NarrationEvidenceVo evidence) {
        return """
                {"jobId":"%s","status":"%s","segmentCount":%d,"totalCharacterCount":%d,"totalTimelineDurationSeconds":"%s","timelineGapCount":%d,"timelineGapSeconds":"%s","timelineHasOverlap":%s,"voicePresetCount":%d,"voiceSummary":"%s","defaultVoice":"%s","audioArtifactCount":%d,"audioLayout":"%s","timeAligned":%s,"narratedVideoArtifactCount":%d,"mixMode":"%s","duckingVolume":%s,"narrationVolume":%s,"fadeDurationMs":%d,"mixSettingsSource":"%s","segmentMixOverrideCount":%d,"segmentMixOverrideSummary":"%s","mixKeyframeCount":%d,"mixKeyframeLaneSummary":"%s"}
                """.formatted(
                json(evidence.jobId()),
                json(evidence.status()),
                evidence.segmentCount(),
                evidence.totalCharacterCount(),
                evidence.totalTimelineDurationSeconds(),
                evidence.timelineGapCount(),
                evidence.timelineGapSeconds(),
                evidence.timelineHasOverlap(),
                evidence.voicePresetCount(),
                json(evidence.voiceSummary()),
                json(evidence.defaultVoice()),
                evidence.audioArtifactCount(),
                json(evidence.audioLayout()),
                evidence.timeAligned(),
                evidence.narratedVideoArtifactCount(),
                json(evidence.mixMode()),
                jsonNumberOrNull(evidence.duckingVolume()),
                jsonNumberOrNull(evidence.narrationVolume()),
                evidence.fadeDurationMs(),
                json(valueOrDefault(evidence.mixSettingsSource(), "N/A")),
                evidence.segmentMixOverrideCount(),
                json(evidence.segmentMixOverrideSummary()),
                evidence.mixKeyframeCount(),
                json(evidence.mixKeyframeLaneSummary())
        );
    }

    private String readme(NarrationEvidenceVo evidence) {
        return """
                # Narration Evidence

                Job: %s
                Status: %s

                This package contains narration metadata only. It excludes narration script bodies, transcript text, subtitle text, media bytes, provider payloads, object storage keys, local filesystem paths, tokens, API keys, and authentication secrets.
                """.formatted(evidence.jobId(), evidence.status());
    }

    private List<NarrationEvidenceCheckVo> checks(int segmentCount, long audioArtifactCount, long narratedVideoArtifactCount) {
        List<NarrationEvidenceCheckVo> checks = new ArrayList<>();
        checks.add(segmentCount > 0
                ? new NarrationEvidenceCheckVo("NARRATION_SEGMENTS", "Narration segments", "READY", segmentCount + " segments saved.")
                : new NarrationEvidenceCheckVo("NARRATION_SEGMENTS", "Narration segments", "BLOCKED", "No narration segments saved."));
        checks.add(audioArtifactCount > 0
                ? new NarrationEvidenceCheckVo("NARRATION_AUDIO", "Narration audio", "READY", audioArtifactCount + " narration audio artifact available with " + TIMED_AUDIO_BED + ".")
                : new NarrationEvidenceCheckVo("NARRATION_AUDIO", "Narration audio", segmentCount > 0 ? "ATTENTION" : "BLOCKED", "No narration audio artifact available."));
        checks.add(narratedVideoArtifactCount > 0
                ? new NarrationEvidenceCheckVo("NARRATED_VIDEO", "Narrated video", "READY", narratedVideoArtifactCount + " narrated video artifact available with " + DUCKED_ORIGINAL_AUDIO + ".")
                : new NarrationEvidenceCheckVo("NARRATED_VIDEO", "Narrated video", segmentCount > 0 ? "ATTENTION" : "BLOCKED", "No narrated video artifact available."));
        return List.copyOf(checks);
    }

    private String status(int segmentCount, long audioArtifactCount, long narratedVideoArtifactCount) {
        if (segmentCount == 0) {
            return "BLOCKED";
        }
        return audioArtifactCount > 0 && narratedVideoArtifactCount > 0 ? "READY" : "ATTENTION";
    }

    private List<NarrationEvidenceLinkVo> safeLinks(String jobId) {
        return List.of(
                link("NARRATION_EVIDENCE", "Narration evidence", "/api/jobs/" + jobId + "/narration-evidence", "application/json"),
                link("NARRATION_EVIDENCE_MARKDOWN", "Narration evidence Markdown", "/api/jobs/" + jobId + "/narration-evidence/markdown/download", "text/markdown"),
                link("NARRATION_EVIDENCE_PACKAGE", "Narration evidence package", "/api/jobs/" + jobId + "/narration-evidence/download", "application/zip"),
                link("NARRATED_VIDEO_GENERATION", "Generate narrated video", "/api/jobs/" + jobId + "/narration-workspace/generate-video", "application/json")
        );
    }

    private List<String> packageEntries(String jobId) {
        return List.of(
                "manifest.json",
                "narration-evidence.md",
                "narration-summary.json",
                "README.md",
                "Linked safe route: /api/jobs/" + jobId + "/narration-evidence/download",
                "Linked safe route: /api/jobs/" + jobId + "/narration-workspace/generate-video"
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "Narration evidence is metadata-only and excludes narration script bodies.",
                "Narration audio is tracked separately from dubbing audio, dubbed video, burned video, and reviewed burned video.",
                "Provider request and response payloads, object keys, local paths, credentials, and media bytes are not included."
        );
    }

    private int totalCharacters(List<NarrationSegmentRecord> segments) {
        return segments.stream()
                .map(NarrationSegmentRecord::text)
                .map(String::trim)
                .mapToInt(String::length)
                .sum();
    }

    private int segmentMixOverrideCount(List<NarrationSegmentRecord> segments) {
        return (int) segments.stream()
                .filter(this::hasSegmentMixOverride)
                .count();
    }

    private String segmentMixOverrideSummary(List<NarrationSegmentRecord> segments) {
        List<String> indexes = segments.stream()
                .filter(this::hasSegmentMixOverride)
                .map(segment -> Integer.toString(segment.segmentIndex()))
                .toList();
        return indexes.isEmpty() ? "none" : "segments=" + String.join(",", indexes);
    }

    private String mixKeyframeLaneSummary(List<NarrationMixKeyframeRecord> keyframes) {
        if (keyframes.isEmpty()) {
            return "none";
        }
        return "DUCKING_VOLUME=" + countLane(keyframes, NarrationMixLane.DUCKING_VOLUME)
                + ",NARRATION_VOLUME=" + countLane(keyframes, NarrationMixLane.NARRATION_VOLUME)
                + ",FADE_DURATION_MS=" + countLane(keyframes, NarrationMixLane.FADE_DURATION_MS);
    }

    private long countLane(List<NarrationMixKeyframeRecord> keyframes, NarrationMixLane lane) {
        return keyframes.stream()
                .filter(keyframe -> keyframe.lane() == lane)
                .count();
    }

    private boolean hasSegmentMixOverride(NarrationSegmentRecord segment) {
        return segment.duckingVolume() != null
                || segment.narrationVolume() != null
                || segment.fadeDurationMs() != null;
    }

    private BigDecimal totalTimelineDurationSeconds(List<NarrationSegmentRecord> segments) {
        return segments.stream()
                .map(segment -> segment.endSeconds().subtract(segment.startSeconds()).setScale(3, RoundingMode.HALF_UP))
                .reduce(ZERO, BigDecimal::add);
    }

    private int timelineGapCount(List<NarrationSegmentRecord> segments) {
        return gapSummary(segments).gapCount();
    }

    private BigDecimal timelineGapSeconds(List<NarrationSegmentRecord> segments) {
        return gapSummary(segments).gapSeconds().setScale(3, RoundingMode.HALF_UP);
    }

    private boolean timelineHasOverlap(List<NarrationSegmentRecord> segments) {
        return gapSummary(segments).hasOverlap();
    }

    private int voicePresetCount(List<NarrationSegmentRecord> segments) {
        return explicitVoices(segments).size();
    }

    private String voiceSummary(List<NarrationSegmentRecord> segments) {
        List<String> voices = explicitVoices(segments);
        if (voices.isEmpty()) {
            return "DEFAULT:" + voiceCatalogService.defaultVoice();
        }
        return voices.size() == 1 ? "PRESET:" + voices.getFirst() : "MIXED";
    }

    private List<String> explicitVoices(List<NarrationSegmentRecord> segments) {
        return segments.stream()
                .map(NarrationSegmentRecord::voice)
                .filter(voice -> voice != null && !voice.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private GapSummary gapSummary(List<NarrationSegmentRecord> segments) {
        BigDecimal gapSeconds = ZERO;
        int gapCount = 0;
        boolean hasOverlap = false;
        List<NarrationSegmentRecord> byTime = segments.stream()
                .sorted(Comparator.comparing(NarrationSegmentRecord::startSeconds))
                .toList();
        for (int index = 1; index < byTime.size(); index += 1) {
            BigDecimal previousEnd = byTime.get(index - 1).endSeconds();
            BigDecimal currentStart = byTime.get(index).startSeconds();
            int comparison = currentStart.compareTo(previousEnd);
            if (comparison > 0) {
                gapCount += 1;
                gapSeconds = gapSeconds.add(currentStart.subtract(previousEnd));
            } else if (comparison < 0) {
                hasOverlap = true;
            }
        }
        return new GapSummary(gapSeconds, gapCount, hasOverlap);
    }

    private record GapSummary(BigDecimal gapSeconds, int gapCount, boolean hasOverlap) {
    }

    private static void writeEntry(ZipOutputStream zipOutputStream, String name, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private static NarrationEvidenceLinkVo link(String kind, String label, String href, String contentType) {
        return new NarrationEvidenceLinkVo(kind, label, href, contentType);
    }

    private static String json(Object value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String jsonNumberOrNull(BigDecimal value) {
        return value == null ? "null" : value.toPlainString();
    }

    private static String valueOrDefault(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static final class EmptyNarrationMixKeyframeRepository implements NarrationMixKeyframeRepository {

        @Override
        public void replaceKeyframes(String jobId, List<NarrationMixKeyframeRecord> keyframes) {
        }

        @Override
        public List<NarrationMixKeyframeRecord> findByJobId(String jobId) {
            return List.of();
        }

        @Override
        public void deleteByJobId(String jobId) {
        }
    }
}
