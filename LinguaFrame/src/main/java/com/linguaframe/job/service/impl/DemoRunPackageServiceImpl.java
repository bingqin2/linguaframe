package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredDemoRunPackageBo;
import com.linguaframe.job.domain.bo.StoredQualityEvidenceBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.DemoRunPackageService;
import com.linguaframe.job.service.JobEvidenceReportService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.QualityEvaluationEvidenceService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DemoRunPackageServiceImpl implements DemoRunPackageService {

    private static final String CONTENT_TYPE = "application/zip";
    private static final Set<JobArtifactType> REVIEWED_SUBTITLE_TYPES = Set.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT
    );
    private static final Set<JobArtifactType> MEDIA_TYPES = Set.of(
            JobArtifactType.DUBBING_AUDIO,
            JobArtifactType.BURNED_VIDEO,
            JobArtifactType.REVIEWED_BURNED_VIDEO
    );
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "/Users/",
            "source-videos/",
            "job-artifacts/",
            "objectKey",
            "demo-access-token",
            "private-demo-token",
            "OPENAI_API_KEY",
            "sk-",
            "raw transcript text",
            "raw subtitle text",
            "raw generated subtitle",
            "raw corrected subtitle",
            "provider payload",
            "provider request payload"
    );

    private final LocalizationJobQueryService queryService;
    private final JobEvidenceReportService evidenceReportService;
    private final QualityEvaluationEvidenceService qualityEvaluationEvidenceService;
    private final DeliveryManifestService deliveryManifestService;
    private final ObjectMapper objectMapper;

    public DemoRunPackageServiceImpl(
            LocalizationJobQueryService queryService,
            JobEvidenceReportService evidenceReportService,
            QualityEvaluationEvidenceService qualityEvaluationEvidenceService,
            DeliveryManifestService deliveryManifestService,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.evidenceReportService = evidenceReportService;
        this.qualityEvaluationEvidenceService = qualityEvaluationEvidenceService;
        this.deliveryManifestService = deliveryManifestService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredDemoRunPackageBo openDemoRunPackage(String jobId) {
        JobDiagnosticsReportVo diagnostics = queryService.getDiagnosticsReport(jobId);
        LocalizationJobVo job = diagnostics.job();
        byte[] qualityEvidence = readQualityEvidence(jobId);

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes)) {
            writeEntry(zipOutputStream, "manifest.json", writeJson(manifest(diagnostics)));
            writeEntry(zipOutputStream, "README.md", readmeMarkdown(diagnostics).getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "job-detail.json", writeJson(safeJobDetail(job)));
            writeEntry(zipOutputStream, "diagnostics.json", writeJson(safeDiagnostics(diagnostics)));
            writeEntry(zipOutputStream, "evidence.md",
                    safeMarkdown("Demo Evidence", evidenceReportService.buildMarkdownReport(jobId), job)
                            .getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "quality-evidence.md",
                    safeMarkdown("Quality Evidence", new String(qualityEvidence, StandardCharsets.UTF_8), job)
                            .getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "delivery-manifest.md",
                    safeMarkdown("Delivery Manifest", deliveryManifestService.buildMarkdownManifest(jobId), job)
                            .getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "demo-handoff-checklist.md",
                    handoffChecklistMarkdown(diagnostics).getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "demo-session-report.md",
                    sessionReportMarkdown(diagnostics).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create demo run package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new StoredDemoRunPackageBo(
                "linguaframe-job-%s-demo-run-package.zip".formatted(safeFilenamePart(job.jobId())),
                CONTENT_TYPE,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private byte[] readQualityEvidence(String jobId) {
        StoredQualityEvidenceBo evidence = qualityEvaluationEvidenceService.openMarkdownEvidence(jobId);
        try {
            return evidence.inputStream().readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read quality evidence Markdown.", ex);
        }
    }

    private Map<String, Object> manifest(JobDiagnosticsReportVo diagnostics) {
        LocalizationJobVo job = diagnostics.job();
        List<String> entries = List.of(
                "manifest.json",
                "README.md",
                "job-detail.json",
                "diagnostics.json",
                "evidence.md",
                "quality-evidence.md",
                "delivery-manifest.md",
                "demo-handoff-checklist.md",
                "demo-session-report.md"
        );
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", Instant.now());
        manifest.put("jobId", job.jobId());
        manifest.put("videoId", job.videoId());
        manifest.put("targetLanguage", job.targetLanguage());
        manifest.put("subtitleStylePreset", job.subtitleStylePreset());
        manifest.put("translationGlossaryEntryCount", job.translationGlossaryEntryCount());
        manifest.put("translationGlossaryHash", job.translationGlossaryHash());
        manifest.put("status", job.status().name());
        manifest.put("artifactCount", diagnostics.artifactCount());
        manifest.put("entries", entries);
        manifest.put("safety", Map.of(
                        "includesMediaBytes", false,
                        "includesUploadedSourceVideo", false,
                        "includesGeneratedArtifacts", false,
                        "includesRawTranscriptText", false,
                        "includesRawSubtitleText", false,
                        "includesObjectKeys", false,
                        "includesProviderPayloads", false,
                        "includesCredentials", false
                ));
        return manifest;
    }

    private Map<String, Object> safeJobDetail(LocalizationJobVo job) {
        Map<String, Object> value = new java.util.LinkedHashMap<>();
        value.put("jobId", job.jobId());
        value.put("videoId", job.videoId());
        value.put("targetLanguage", job.targetLanguage());
        value.put("subtitleStylePreset", job.subtitleStylePreset());
        value.put("translationGlossaryEntryCount", job.translationGlossaryEntryCount());
        value.put("translationGlossaryHash", job.translationGlossaryHash());
        value.put("ttsVoice", job.ttsVoice());
        value.put("status", job.status().name());
        value.put("createdAt", job.createdAt());
        value.put("startedAt", job.startedAt());
        value.put("completedAt", job.completedAt());
        value.put("failedAt", job.failedAt());
        value.put("failureStage", job.failureStage() == null ? null : job.failureStage().name());
        value.put("retryCount", job.retryCount());
        value.put("dispatchStatus", job.dispatchStatus() == null ? null : job.dispatchStatus().name());
        value.put("dispatchAttempts", job.dispatchAttempts());
        value.put("dispatchedAt", job.dispatchedAt());
        value.put("usageSummary", job.usageSummary());
        value.put("cacheSummary", job.cacheSummary());
        value.put("modelCalls", job.modelCalls());
        value.put("qualityEvaluation", job.qualityEvaluation());
        value.put("failureTriage", job.failureTriage());
        value.put("pipelineProgress", job.pipelineProgress());
        return value;
    }

    private Map<String, Object> safeDiagnostics(JobDiagnosticsReportVo diagnostics) {
        return Map.of(
                "generatedAt", diagnostics.generatedAt(),
                "job", safeJobDetail(diagnostics.job()),
                "artifacts", diagnostics.artifacts(),
                "artifactCount", diagnostics.artifactCount()
        );
    }

    private String readmeMarkdown(JobDiagnosticsReportVo diagnostics) {
        LocalizationJobVo job = diagnostics.job();
        return String.join("\n",
                "# LinguaFrame Demo Run Package",
                "",
                "- Job: " + job.jobId(),
                "- Video: " + job.videoId(),
                "- Target language: " + job.targetLanguage(),
                "- Subtitle style: " + job.subtitleStylePreset(),
                "- Status: " + job.status(),
                "- Generated at: " + Instant.now(),
                "",
                "## Entries",
                "- job-detail.json",
                "- diagnostics.json",
                "- evidence.md",
                "- quality-evidence.md",
                "- delivery-manifest.md",
                "- demo-handoff-checklist.md",
                "- demo-session-report.md",
                "",
                "## Safe Routes",
                "- Job detail: /api/jobs/" + job.jobId(),
                "- Diagnostics: /api/jobs/" + job.jobId() + "/diagnostics/download",
                "- Evidence bundle: /api/jobs/" + job.jobId() + "/evidence/bundle/download",
                "- Quality evidence: /api/jobs/" + job.jobId() + "/quality-evaluation/evidence/markdown/download",
                "- Demo run package: /api/jobs/" + job.jobId() + "/demo-run-package/download",
                "",
                "## Safety",
                "- Includes metadata and safe reports only.",
                "- Excludes media bytes, transcript content, subtitle content, object keys, local paths, credentials, demo tokens, and provider payloads.",
                ""
        );
    }

    private String handoffChecklistMarkdown(JobDiagnosticsReportVo diagnostics) {
        LocalizationJobVo job = diagnostics.job();
        long reviewedSubtitleCount = reviewedSubtitleCount(diagnostics.artifacts());
        long mediaCount = mediaCount(diagnostics.artifacts());
        boolean terminal = job.status() == LocalizationJobStatus.COMPLETED
                || job.status() == LocalizationJobStatus.FAILED
                || job.status() == LocalizationJobStatus.CANCELLED
                || (job.pipelineProgress() != null && job.pipelineProgress().terminal());
        return String.join("\n",
                "# LinguaFrame Demo Handoff Checklist",
                "",
                "- " + passWarn(job.status() == LocalizationJobStatus.COMPLETED) + " Job completed",
                "- " + passWarn(terminal) + " Pipeline terminal",
                "- " + passWarn(reviewedSubtitleCount >= 3) + " Reviewed subtitles ready",
                "- " + passWarn(mediaCount > 0) + " Media outputs available",
                "- PASS Evidence downloads ready",
                "- " + passWarn(job.qualityEvaluation() != null) + " Quality evaluation available",
                "- " + passWarn(job.usageSummary() != null && job.usageSummary().modelCallCount() > 0)
                        + " Cost and model-call evidence available",
                "- " + passWarn(job.cacheSummary() != null
                        && (job.cacheSummary().cacheHitCount() > 0 || job.cacheSummary().providerCacheHitCount() > 0))
                        + " Cache evidence available",
                ""
        );
    }

    private String sessionReportMarkdown(JobDiagnosticsReportVo diagnostics) {
        LocalizationJobVo job = diagnostics.job();
        QualityEvaluationVo quality = job.qualityEvaluation();
        long reviewedSubtitleCount = reviewedSubtitleCount(diagnostics.artifacts());
        long mediaCount = mediaCount(diagnostics.artifacts());
        boolean ready = job.status() == LocalizationJobStatus.COMPLETED && reviewedSubtitleCount >= 3;
        List<String> lines = new java.util.ArrayList<>();
        lines.add("# LinguaFrame Demo Session Report");
        lines.add("");
        lines.add("- Job: " + job.jobId());
        lines.add("- Overall: " + (ready ? "READY" : "ATTENTION"));
        lines.add("- Video: " + job.videoId());
        lines.add("- Target language: " + job.targetLanguage());
        lines.add("- Subtitle style: " + job.subtitleStylePreset());
        lines.add("- Status: " + job.status());
        lines.add("- Artifacts: " + diagnostics.artifactCount());
        lines.add("- Reviewed subtitle artifacts: " + reviewedSubtitleCount);
        lines.add("- Media outputs: " + mediaCount);
        if (job.usageSummary() != null) {
            lines.add("- Model calls: " + job.usageSummary().modelCallCount());
            lines.add("- Failed model calls: " + job.usageSummary().failedModelCallCount());
            lines.add("- Estimated cost USD: " + job.usageSummary().estimatedCostUsd());
        }
        if (job.cacheSummary() != null) {
            lines.add("- Artifact cache hits: " + job.cacheSummary().cacheHitCount());
            lines.add("- Provider cache hits: " + job.cacheSummary().providerCacheHitCount());
        }
        if (quality == null) {
            lines.add("- Quality: NOT_RECORDED");
        } else {
            lines.add("- Quality: %d / 100, %s, %s".formatted(
                    quality.score(),
                    quality.verdict(),
                    quality.status()
            ));
        }
        lines.add("- Demo run package: /api/jobs/" + job.jobId() + "/demo-run-package/download");
        lines.add("");
        return String.join("\n", lines);
    }

    private String safeMarkdown(String title, String markdown, LocalizationJobVo job) {
        if (!containsForbiddenMarker(markdown)) {
            return markdown;
        }
        return String.join("\n",
                "# LinguaFrame " + title,
                "",
                "- Job: " + job.jobId(),
                "- Status: " + job.status(),
                "- Original report was omitted from this package because it contained fields outside the package safety contract.",
                "- Use diagnostics and job detail entries in this package for safe metadata.",
                ""
        );
    }

    private boolean containsForbiddenMarker(String text) {
        return FORBIDDEN_MARKERS.stream().anyMatch(text::contains);
    }

    private long reviewedSubtitleCount(List<JobDiagnosticsArtifactVo> artifacts) {
        return artifacts.stream().filter(artifact -> REVIEWED_SUBTITLE_TYPES.contains(artifact.type())).count();
    }

    private long mediaCount(List<JobDiagnosticsArtifactVo> artifacts) {
        return artifacts.stream().filter(artifact -> MEDIA_TYPES.contains(artifact.type())).count();
    }

    private String passWarn(boolean value) {
        return value ? "PASS" : "WARN";
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write demo run package JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private String safeFilenamePart(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "-");
    }
}
