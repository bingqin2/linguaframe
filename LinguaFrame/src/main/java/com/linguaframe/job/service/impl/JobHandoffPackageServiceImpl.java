package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredHandoffPackageBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.service.DeliveryManifestService;
import com.linguaframe.job.service.JobEvidenceReportService;
import com.linguaframe.job.service.JobHandoffPackageService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.storage.service.ObjectStorageService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class JobHandoffPackageServiceImpl implements JobHandoffPackageService {

    private static final String CONTENT_TYPE = "application/zip";
    private static final Set<JobArtifactType> HANDOFF_TYPES = EnumSet.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT,
            JobArtifactType.REVIEWED_BURNED_VIDEO
    );
    private static final Set<JobArtifactType> REQUIRED_REVIEWED_SUBTITLE_TYPES = EnumSet.of(
            JobArtifactType.REVIEWED_SUBTITLE_JSON,
            JobArtifactType.REVIEWED_SUBTITLE_SRT,
            JobArtifactType.REVIEWED_SUBTITLE_VTT
    );

    private final JobArtifactRepository artifactRepository;
    private final ObjectStorageService objectStorageService;
    private final LocalizationJobQueryService queryService;
    private final DeliveryManifestService deliveryManifestService;
    private final JobEvidenceReportService evidenceReportService;
    private final ObjectMapper objectMapper;

    public JobHandoffPackageServiceImpl(
            JobArtifactRepository artifactRepository,
            ObjectStorageService objectStorageService,
            LocalizationJobQueryService queryService,
            DeliveryManifestService deliveryManifestService,
            JobEvidenceReportService evidenceReportService,
            ObjectMapper objectMapper
    ) {
        this.artifactRepository = artifactRepository;
        this.objectStorageService = objectStorageService;
        this.queryService = queryService;
        this.deliveryManifestService = deliveryManifestService;
        this.evidenceReportService = evidenceReportService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredHandoffPackageBo openHandoffPackage(String jobId) {
        JobDiagnosticsReportVo diagnostics = queryService.getDiagnosticsReport(jobId);
        List<JobArtifactRecord> reviewedArtifacts = artifactRepository.findByJobId(jobId)
                .stream()
                .filter(artifact -> HANDOFF_TYPES.contains(artifact.type()))
                .toList();
        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes)) {
            writeEntry(zipOutputStream, "manifest.json", writeJson(manifest(diagnostics, reviewedArtifacts)));
            writeEntry(zipOutputStream, "delivery-manifest.md",
                    deliveryManifestService.buildMarkdownManifest(jobId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "evidence.md",
                    evidenceReportService.buildMarkdownReport(jobId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "diagnostics.json", writeJson(diagnostics));
            for (JobArtifactRecord artifact : reviewedArtifacts) {
                zipOutputStream.putNextEntry(new ZipEntry(reviewedEntryName(artifact)));
                try (InputStream artifactInputStream = objectStorageService.open(artifact.objectKey())) {
                    artifactInputStream.transferTo(zipOutputStream);
                }
                zipOutputStream.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create job handoff package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new StoredHandoffPackageBo(
                "linguaframe-job-%s-handoff-package.zip".formatted(jobId),
                CONTENT_TYPE,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private Map<String, Object> manifest(JobDiagnosticsReportVo diagnostics, List<JobArtifactRecord> reviewedArtifacts) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("generatedAt", Instant.now());
        manifest.put("jobId", diagnostics.job().jobId());
        manifest.put("videoId", diagnostics.job().videoId());
        manifest.put("targetLanguage", diagnostics.job().targetLanguage());
        manifest.put("demoProfileId", diagnostics.job().demoProfileId());
        manifest.put("subtitleStylePreset", diagnostics.job().subtitleStylePreset());
        manifest.put("translationGlossaryEntryCount", diagnostics.job().translationGlossaryEntryCount());
        manifest.put("translationGlossaryHash", diagnostics.job().translationGlossaryHash());
        manifest.put("subtitlePolishingMode", diagnostics.job().subtitlePolishingMode());
        manifest.put("status", diagnostics.job().status().name());
        manifest.put("handoffReady", handoffReady(reviewedArtifacts));
        manifest.put("reviewedArtifactCount", reviewedArtifacts.size());
        manifest.put("entries", packageEntries(reviewedArtifacts));
        manifest.put("safety", Map.of(
                        "includesUploadedSourceVideo", false,
                        "includesGeneratedAuditArtifacts", false,
                        "includesRawTranscriptText", false,
                        "includesObjectKeys", false,
                        "includesProviderPayloads", false
                ));
        return manifest;
    }

    private boolean handoffReady(List<JobArtifactRecord> reviewedArtifacts) {
        return REQUIRED_REVIEWED_SUBTITLE_TYPES.stream()
                .allMatch(type -> reviewedArtifacts.stream().anyMatch(artifact -> artifact.type() == type));
    }

    private List<String> packageEntries(List<JobArtifactRecord> reviewedArtifacts) {
        return java.util.stream.Stream.concat(
                java.util.stream.Stream.of("manifest.json", "delivery-manifest.md", "evidence.md", "diagnostics.json"),
                reviewedArtifacts.stream().map(this::reviewedEntryName)
        ).toList();
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write handoff package JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private String reviewedEntryName(JobArtifactRecord artifact) {
        return "reviewed/%s/%s-%s".formatted(
                artifact.type().name(),
                artifact.id(),
                sanitizeFilename(artifact.filename())
        );
    }

    private String sanitizeFilename(String filename) {
        String basename = filename == null ? "artifact" : filename.replace('\\', '/');
        int slashIndex = basename.lastIndexOf('/');
        if (slashIndex >= 0) {
            basename = basename.substring(slashIndex + 1);
        }
        basename = basename.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        basename = basename.replaceAll("^-+", "").replaceAll("-+$", "");
        return basename.isBlank() || ".".equals(basename) || "..".equals(basename) ? "artifact" : basename;
    }
}
