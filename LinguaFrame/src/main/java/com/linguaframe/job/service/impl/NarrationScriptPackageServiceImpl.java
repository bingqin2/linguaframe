package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredNarrationScriptPackageBo;
import com.linguaframe.job.domain.entity.NarrationMixSettingsRecord;
import com.linguaframe.job.domain.entity.NarrationSegmentRecord;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.NarrationMixSettingsVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageCheckVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageLinkVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageSegmentVo;
import com.linguaframe.job.domain.vo.NarrationScriptPackageVo;
import com.linguaframe.job.repository.NarrationMixSettingsRepository;
import com.linguaframe.job.repository.NarrationSegmentRepository;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.NarrationScriptPackageService;
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
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class NarrationScriptPackageServiceImpl implements NarrationScriptPackageService {

    private static final BigDecimal ZERO = new BigDecimal("0.000");
    private static final BigDecimal DEFAULT_DUCKING_VOLUME = new BigDecimal("0.350");
    private static final BigDecimal DEFAULT_NARRATION_VOLUME = new BigDecimal("1.000");
    private static final int DEFAULT_FADE_DURATION_MS = 250;

    private final NarrationSegmentRepository segmentRepository;
    private final NarrationMixSettingsRepository mixSettingsRepository;
    private final LocalizationJobQueryService queryService;
    private final NarrationVoiceCatalogService voiceCatalogService;
    private final ObjectMapper objectMapper;

    public NarrationScriptPackageServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobQueryService queryService,
            NarrationVoiceCatalogService voiceCatalogService
    ) {
        this(segmentRepository, mixSettingsRepository, queryService, voiceCatalogService, new ObjectMapper().findAndRegisterModules());
    }

    @Autowired
    public NarrationScriptPackageServiceImpl(
            NarrationSegmentRepository segmentRepository,
            NarrationMixSettingsRepository mixSettingsRepository,
            LocalizationJobQueryService queryService,
            NarrationVoiceCatalogService voiceCatalogService,
            ObjectMapper objectMapper
    ) {
        this.segmentRepository = segmentRepository;
        this.mixSettingsRepository = mixSettingsRepository;
        this.queryService = queryService;
        this.voiceCatalogService = voiceCatalogService;
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
                null,
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
