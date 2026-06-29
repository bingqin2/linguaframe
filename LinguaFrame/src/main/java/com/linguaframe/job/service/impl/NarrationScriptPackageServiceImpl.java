package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageDto;
import com.linguaframe.job.domain.dto.ImportNarrationScriptPackageSegmentDto;
import com.linguaframe.job.domain.dto.SaveNarrationSegmentsRequest;
import com.linguaframe.job.domain.dto.UpdateNarrationMixSettingsDto;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationMixSettingsVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageCheckVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageImportVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageLinkVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageSegmentVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.domain.vo.NarrationWorkspaceVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationScriptPackageService;
import com.linguaframe.job.service.NarrationVoiceCatalogService;
import com.linguaframe.job.service.NarrationWorkspaceService;
import com.linguaframe.media.repository.VideoRepository;
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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NarrationScriptPackageServiceImpl implements NarrationScriptPackageService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final BigDecimal DEFAULT_DUCKING_VOLUME = new BigDecimal("0.350");
    private static final BigDecimal DEFAULT_NARRATION_VOLUME = new BigDecimal("1.000");
    private static final int DEFAULT_FADE_DURATION_MS = 250;
    private static final int MAX_SEGMENTS = 20;
    private static final int MAX_TEXT_LENGTH = 1000;
    private static final int MAX_VOICE_LENGTH = 64;
    private static final BigDecimal MAX_DUCKING_VOLUME = new BigDecimal("1.000");
    private static final BigDecimal MAX_NARRATION_VOLUME = new BigDecimal("2.000");
    private static final int MAX_FADE_DURATION_MS = 5000;

    private final NarrationSegmentRepository segmentRepository;
    private final NarrationMixSettingsRepository mixSettingsRepository;
    private final LocalizationJobQueryService queryService;
    private final NarrationVoiceCatalogService voiceCatalogService;
    private final NarrationWorkspaceService workspaceService;
    private final VideoRepository videoRepository;
    private final ObjectMapper objectMapper;

    public NarrationScriptPackageServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobQueryService queryService,
            NarrationVoiceCatalogService voiceCatalogService
    ) {
        this(
                segmentRepository,
                mixSettingsRepository,
                queryService,
                voiceCatalogService,
                null,
                null,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    public NarrationScriptPackageServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobQueryService queryService,
            NarrationVoiceCatalogService voiceCatalogService,
            NarrationWorkspaceService workspaceService,
            VideoRepository videoRepository
    ) {
        this(
                segmentRepository,
                mixSettingsRepository,
                queryService,
                voiceCatalogService,
                workspaceService,
                videoRepository,
                new ObjectMapper().findAndRegisterModules()
        );
    }

    @Autowired
    public NarrationScriptPackageServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobQueryService queryService,
            NarrationVoiceCatalogService voiceCatalogService,
            NarrationWorkspaceService workspaceService,
            VideoRepository videoRepository,
            ObjectMapper objectMapper
    ) {
        this.segmentRepository = segmentRepository;
        this.mixSettingsRepository = mixSettingsRepository;
        this.queryService = queryService;
        this.voiceCatalogService = voiceCatalogService;
        this.workspaceService = workspaceService;
        this.videoRepository = videoRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public NarrationScriptPackageVo getPackage(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        List<NarrationScriptPackageSegmentVo> segments = segmentRepository.findByJobId(jobId).stream()
                .sorted(Comparator.comparingInt(NarrationSegmentRecord::segmentIndex))
                .map(this::toSegment)
                .toList();
        GapSummary gapSummary = gapSummary(segments);
        return new NarrationScriptPackageVo(
                jobId,
                job.targetLanguage(),
                sourceDurationSeconds(job),
                segments.isEmpty() ? "BLOCKED" : "READY",
                segments.size(),
                totalCharacters(segments),
                totalDuration(segments),
                gapSummary.gapCount(),
                gapSummary.gapSeconds().setScale(3, RoundingMode.HALF_UP),
                gapSummary.hasOverlap(),
                voiceSummary(segments),
                voiceCatalogService.defaultVoice(),
                mixSettings(jobId),
                voiceCatalogService.catalog(),
                segments,
                checks(segments, gapSummary),
                safeLinks(jobId),
                packageEntries(jobId),
                safetyNotes()
        );
    }

    @Override
    public String renderMarkdown(String jobId) {
        NarrationScriptPackageVo scriptPackage = getPackage(jobId);
        List<String> lines = new ArrayList<>();
        lines.add("# Narration Script Package");
        lines.add("");
        lines.add("- Job: " + scriptPackage.jobId());
        lines.add("- Target language: " + scriptPackage.targetLanguage());
        lines.add("- Status: " + scriptPackage.status());
        lines.add("- Segment count: " + scriptPackage.segmentCount());
        lines.add("- Total narration characters: " + scriptPackage.totalCharacterCount());
        lines.add("- Total timeline duration seconds: " + scriptPackage.totalTimelineDurationSeconds());
        lines.add("- Timeline gap count: " + scriptPackage.timelineGapCount());
        lines.add("- Timeline gap seconds: " + scriptPackage.timelineGapSeconds());
        lines.add("- Timeline has overlap: " + scriptPackage.timelineHasOverlap());
        lines.add("- Voice summary: " + scriptPackage.voiceSummary());
        lines.add("- Default voice: " + scriptPackage.defaultVoice());
        lines.add("- Ducking volume: " + scriptPackage.mixSettings().duckingVolume());
        lines.add("- Narration volume: " + scriptPackage.mixSettings().narrationVolume());
        lines.add("- Fade duration ms: " + scriptPackage.mixSettings().fadeDurationMs());
        lines.add("- Includes narration text bodies: true");
        lines.add("");
        lines.add("## Segments");
        if (scriptPackage.segments().isEmpty()) {
            lines.add("- No narration segments saved.");
        }
        for (NarrationScriptPackageSegmentVo segment : scriptPackage.segments()) {
            lines.add("- " + segment.index() + ": " + segment.startSeconds() + "-" + segment.endSeconds()
                    + " (" + segment.durationSeconds() + "s), voice=" + valueOrInherit(segment.voice())
                    + ", characters=" + segment.characterCount());
            lines.add("  Text: " + segment.text());
        }
        lines.add("");
        lines.add("## Checks");
        for (NarrationScriptPackageCheckVo check : scriptPackage.checks()) {
            lines.add("- " + check.label() + ": " + check.status() + " - " + check.detail());
        }
        lines.add("");
        lines.add("## Safe Links");
        for (NarrationScriptPackageLinkVo link : scriptPackage.safeLinks()) {
            lines.add("- " + link.label() + ": " + link.href());
        }
        lines.add("");
        lines.add("## Package Entries");
        for (String entry : scriptPackage.packageEntries()) {
            lines.add("- " + entry);
        }
        lines.add("");
        return String.join("\n", lines);
    }

    @Override
    public StoredNarrationScriptPackageBo openPackage(String jobId) {
        NarrationScriptPackageVo scriptPackage = getPackage(jobId);
        String markdown = renderMarkdown(jobId);
        byte[] content = zipBytes(scriptPackage, markdown);
        return new StoredNarrationScriptPackageBo(
                "linguaframe-job-" + jobId + "-narration-script-package.zip",
                "application/zip",
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    @Override
    public NarrationScriptPackageImportVo importPackage(String jobId, ImportNarrationScriptPackageDto request) {
        if (workspaceService == null) {
            throw new IllegalStateException("Narration script package import is not configured.");
        }
        LocalizationJobVo job = queryService.getJob(jobId);
        if (request == null || !Boolean.TRUE.equals(request.replaceExisting())) {
            throw new IllegalArgumentException("Narration script package import requires replaceExisting=true.");
        }
        List<ImportNarrationScriptPackageSegmentDto> segments = request.segments() == null
                ? List.of()
                : request.segments();
        validateImportSegments(segments, sourceDurationSeconds(job));
        validateImportMixSettings(request.mixSettings());

        NarrationWorkspaceVo workspace = workspaceService.saveWorkspace(
                jobId,
                new SaveNarrationSegmentsRequest(segments.stream()
                        .sorted(Comparator.comparingInt(ImportNarrationScriptPackageSegmentDto::index))
                        .map(segment -> new SaveNarrationSegmentsRequest.Segment(
                                segment.index(),
                                normalizeSeconds(segment.startSeconds(), "startSeconds"),
                                normalizeSeconds(segment.endSeconds(), "endSeconds"),
                                segment.text().trim(),
                                normalizeVoice(segment.voice())
                        ))
                        .toList())
        );
        if (request.mixSettings() != null) {
            workspace = workspaceService.updateMixSettings(jobId, request.mixSettings());
        }
        return new NarrationScriptPackageImportVo(
                jobId,
                segments.size(),
                segments.stream().mapToInt(segment -> segment.text().trim().length()).sum(),
                voiceSummaryFromImport(segments),
                true,
                List.of(),
                workspace
        );
    }

    private byte[] zipBytes(NarrationScriptPackageVo scriptPackage, String markdown) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            writeEntry(zipOutputStream, "manifest.json", writeJson(Map.of(
                    "jobId", scriptPackage.jobId(),
                    "status", scriptPackage.status(),
                    "segmentCount", scriptPackage.segmentCount(),
                    "voiceSummary", scriptPackage.voiceSummary(),
                    "timelineGapCount", scriptPackage.timelineGapCount(),
                    "includesNarrationTextBodies", true,
                    "entries", scriptPackage.packageEntries()
            )));
            writeEntry(zipOutputStream, "narration-script-package.json", writeJson(scriptPackage));
            writeEntry(zipOutputStream, "narration-script-package.md", markdown);
            writeEntry(zipOutputStream, "README.md", readme(scriptPackage));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to build narration script package.", ex);
        }
        return outputStream.toByteArray();
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, String value) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(value.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to render narration script package JSON.", ex);
        }
    }

    private String readme(NarrationScriptPackageVo scriptPackage) {
        return """
                # Narration Script Package

                Job: %s
                Status: %s

                This package intentionally includes operator-authored narration script text so the narration workspace can be reviewed, moved, and restored. It excludes media bytes, transcript text, subtitle text, provider payloads, object storage keys, local filesystem paths, tokens, API keys, and authentication secrets.
                """.formatted(scriptPackage.jobId(), scriptPackage.status());
    }

    private NarrationScriptPackageSegmentVo toSegment(NarrationSegmentRecord record) {
        BigDecimal duration = record.endSeconds().subtract(record.startSeconds()).setScale(3, RoundingMode.HALF_UP);
        String text = record.text().trim();
        return new NarrationScriptPackageSegmentVo(
                record.segmentIndex(),
                record.startSeconds().setScale(3, RoundingMode.HALF_UP),
                record.endSeconds().setScale(3, RoundingMode.HALF_UP),
                duration,
                text,
                record.voice(),
                text.length(),
                record.updatedAt()
        );
    }

    private BigDecimal sourceDurationSeconds(LocalizationJobVo job) {
        if (job == null || job.videoId() == null || videoRepository == null) {
            return null;
        }
        return videoRepository.findById(job.videoId())
                .map(video -> video.durationSeconds() == null ? null : BigDecimal.valueOf(video.durationSeconds()).setScale(3, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private NarrationMixSettingsVo mixSettings(String jobId) {
        return mixSettingsRepository.findByJobId(jobId)
                .map(record -> new NarrationMixSettingsVo(
                        record.duckingVolume(),
                        record.narrationVolume(),
                        record.fadeDurationMs(),
                        record.updatedAt()
                ))
                .orElseGet(() -> new NarrationMixSettingsVo(
                        DEFAULT_DUCKING_VOLUME,
                        DEFAULT_NARRATION_VOLUME,
                        DEFAULT_FADE_DURATION_MS,
                        null
                ));
    }

    private List<NarrationScriptPackageCheckVo> checks(
            List<NarrationScriptPackageSegmentVo> segments,
            GapSummary gapSummary
    ) {
        List<NarrationScriptPackageCheckVo> checks = new ArrayList<>();
        checks.add(segments.isEmpty()
                ? new NarrationScriptPackageCheckVo("SCRIPT_SEGMENTS", "Script segments", "BLOCKED", "No narration segments saved.")
                : new NarrationScriptPackageCheckVo("SCRIPT_SEGMENTS", "Script segments", "READY", segments.size() + " narration segments saved."));
        checks.add(gapSummary.hasOverlap()
                ? new NarrationScriptPackageCheckVo("SCRIPT_TIMELINE", "Script timeline", "BLOCKED", "Narration segments overlap.")
                : new NarrationScriptPackageCheckVo("SCRIPT_TIMELINE", "Script timeline", segments.isEmpty() ? "BLOCKED" : "READY", "Timeline ranges are non-overlapping."));
        checks.add(new NarrationScriptPackageCheckVo("VOICE_PRESETS", "Voice presets", segments.isEmpty() ? "ATTENTION" : "READY", "Voice values are stored as provider preset identifiers or default inheritance."));
        return List.copyOf(checks);
    }

    private List<NarrationScriptPackageLinkVo> safeLinks(String jobId) {
        return List.of(
                link("NARRATION_SCRIPT_PACKAGE", "Narration script package", "/api/jobs/" + jobId + "/narration-script-package", "application/json"),
                link("NARRATION_SCRIPT_PACKAGE_MARKDOWN", "Narration script package Markdown", "/api/jobs/" + jobId + "/narration-script-package/markdown/download", "text/markdown"),
                link("NARRATION_SCRIPT_PACKAGE_ZIP", "Narration script package ZIP", "/api/jobs/" + jobId + "/narration-script-package/download", "application/zip")
        );
    }

    private NarrationScriptPackageLinkVo link(String kind, String label, String href, String contentType) {
        return new NarrationScriptPackageLinkVo(kind, label, href, contentType);
    }

    private List<String> packageEntries(String jobId) {
        return List.of(
                "manifest.json",
                "narration-script-package.json",
                "narration-script-package.md",
                "README.md",
                "Linked safe route: /api/jobs/" + jobId + "/narration-script-package/download"
        );
    }

    private List<String> safetyNotes() {
        return List.of(
                "This package intentionally includes operator-authored narration script text for workspace reuse.",
                "General narration evidence remains metadata-only and excludes narration script bodies.",
                "Media bytes, transcript text, subtitle text, provider payloads, object keys, local paths, tokens, API keys, and credentials are excluded."
        );
    }

    private int totalCharacters(List<NarrationScriptPackageSegmentVo> segments) {
        return segments.stream().mapToInt(NarrationScriptPackageSegmentVo::characterCount).sum();
    }

    private void validateImportSegments(
            List<ImportNarrationScriptPackageSegmentDto> segments,
            BigDecimal durationSeconds
    ) {
        if (segments.size() > MAX_SEGMENTS) {
            throw new IllegalArgumentException("Narration workspace supports at most 20 segments.");
        }
        List<ImportNarrationScriptPackageSegmentDto> byIndex = segments.stream()
                .sorted(Comparator.comparingInt(ImportNarrationScriptPackageSegmentDto::index))
                .toList();
        for (int i = 0; i < byIndex.size(); i++) {
            ImportNarrationScriptPackageSegmentDto segment = byIndex.get(i);
            if (segment.index() != i) {
                throw new IllegalArgumentException("Narration segment indexes must start at 0 and be contiguous.");
            }
            validateImportSegment(segment, durationSeconds);
        }
        List<ImportNarrationScriptPackageSegmentDto> byTime = segments.stream()
                .sorted(Comparator.comparing(segment -> normalizeSeconds(segment.startSeconds(), "startSeconds")))
                .toList();
        for (int i = 1; i < byTime.size(); i++) {
            BigDecimal previousEnd = normalizeSeconds(byTime.get(i - 1).endSeconds(), "endSeconds");
            BigDecimal currentStart = normalizeSeconds(byTime.get(i).startSeconds(), "startSeconds");
            if (currentStart.compareTo(previousEnd) < 0) {
                throw new IllegalArgumentException("Narration script package segments must not overlap.");
            }
        }
    }

    private void validateImportSegment(ImportNarrationScriptPackageSegmentDto segment, BigDecimal durationSeconds) {
        BigDecimal start = normalizeSeconds(segment.startSeconds(), "startSeconds");
        BigDecimal end = normalizeSeconds(segment.endSeconds(), "endSeconds");
        if (start.compareTo(ZERO) < 0) {
            throw new IllegalArgumentException("Narration startSeconds must be greater than or equal to 0.");
        }
        if (end.compareTo(start) <= 0) {
            throw new IllegalArgumentException("Narration endSeconds must be greater than startSeconds.");
        }
        if (durationSeconds != null && end.compareTo(durationSeconds) > 0) {
            throw new IllegalArgumentException("Narration script package segment endSeconds must be within source duration.");
        }
        if (segment.text() == null || segment.text().trim().isEmpty()) {
            throw new IllegalArgumentException("Narration text must not be blank.");
        }
        if (segment.text().trim().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Narration text must be at most 1000 characters.");
        }
        String voice = normalizeVoice(segment.voice());
        if (voice != null && voice.length() > MAX_VOICE_LENGTH) {
            throw new IllegalArgumentException("Narration voice must be at most 64 characters.");
        }
        if (!voiceCatalogService.containsVoice(voice)) {
            throw new IllegalArgumentException("Narration voice must be one of the configured presets.");
        }
    }

    private void validateImportMixSettings(UpdateNarrationMixSettingsDto request) {
        if (request == null) {
            return;
        }
        normalizeMixDecimal(request.duckingVolume(), "duckingVolume", MAX_DUCKING_VOLUME);
        normalizeMixDecimal(request.narrationVolume(), "narrationVolume", MAX_NARRATION_VOLUME);
        normalizeFadeDuration(request.fadeDurationMs());
    }

    private BigDecimal normalizeSeconds(BigDecimal value, String label) {
        if (value == null) {
            throw new IllegalArgumentException("Narration " + label + " is required.");
        }
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private String normalizeVoice(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private BigDecimal normalizeMixDecimal(BigDecimal value, String label, BigDecimal max) {
        if (value == null) {
            throw new IllegalArgumentException("Narration " + label + " is required.");
        }
        BigDecimal normalized = value.setScale(3, RoundingMode.HALF_UP);
        if (normalized.compareTo(ZERO) < 0 || normalized.compareTo(max) > 0) {
            throw new IllegalArgumentException(label + " must be between 0.00 and " + max.setScale(2, RoundingMode.HALF_UP).toPlainString() + ".");
        }
        return normalized;
    }

    private int normalizeFadeDuration(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("Narration fadeDurationMs is required.");
        }
        if (value < 0 || value > MAX_FADE_DURATION_MS) {
            throw new IllegalArgumentException("fadeDurationMs must be between 0 and 5000.");
        }
        return value;
    }

    private BigDecimal totalDuration(List<NarrationScriptPackageSegmentVo> segments) {
        return segments.stream()
                .map(NarrationScriptPackageSegmentVo::durationSeconds)
                .reduce(ZERO, BigDecimal::add)
                .setScale(3, RoundingMode.HALF_UP);
    }

    private String voiceSummary(List<NarrationScriptPackageSegmentVo> segments) {
        List<String> voices = segments.stream()
                .map(NarrationScriptPackageSegmentVo::voice)
                .filter(voice -> voice != null && !voice.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (voices.isEmpty()) {
            return "DEFAULT:" + voiceCatalogService.defaultVoice();
        }
        return voices.size() == 1 ? "PRESET:" + voices.getFirst() : "MIXED";
    }

    private String voiceSummaryFromImport(List<ImportNarrationScriptPackageSegmentDto> segments) {
        List<String> voices = segments.stream()
                .map(ImportNarrationScriptPackageSegmentDto::voice)
                .map(this::normalizeVoice)
                .filter(voice -> voice != null && !voice.isBlank())
                .distinct()
                .toList();
        if (voices.isEmpty()) {
            return "DEFAULT:" + voiceCatalogService.defaultVoice();
        }
        return voices.size() == 1 ? "PRESET:" + voices.getFirst() : "MIXED";
    }

    private GapSummary gapSummary(List<NarrationScriptPackageSegmentVo> segments) {
        BigDecimal gapSeconds = ZERO;
        int gapCount = 0;
        boolean hasOverlap = false;
        List<NarrationScriptPackageSegmentVo> byTime = segments.stream()
                .sorted(Comparator.comparing(NarrationScriptPackageSegmentVo::startSeconds))
                .toList();
        for (int i = 1; i < byTime.size(); i++) {
            BigDecimal previousEnd = byTime.get(i - 1).endSeconds();
            BigDecimal currentStart = byTime.get(i).startSeconds();
            int comparison = currentStart.compareTo(previousEnd);
            if (comparison > 0) {
                gapCount++;
                gapSeconds = gapSeconds.add(currentStart.subtract(previousEnd));
            } else if (comparison < 0) {
                hasOverlap = true;
            }
        }
        return new GapSummary(gapCount, gapSeconds, hasOverlap);
    }

    private String valueOrInherit(String value) {
        return value == null || value.isBlank() ? "INHERIT_DEFAULT" : value;
    }

    private record GapSummary(int gapCount, BigDecimal gapSeconds, boolean hasOverlap) {
    }
}
