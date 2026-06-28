package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredEvidenceBundleBo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.service.JobEvidenceBundleService;
import com.linguaframe.job.service.JobEvidenceReportService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class JobEvidenceBundleServiceImpl implements JobEvidenceBundleService {

    private static final String CONTENT_TYPE = "application/zip";

    private final LocalizationJobQueryService queryService;
    private final JobEvidenceReportService evidenceReportService;
    private final ObjectMapper objectMapper;

    public JobEvidenceBundleServiceImpl(
            LocalizationJobQueryService queryService,
            JobEvidenceReportService evidenceReportService,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.evidenceReportService = evidenceReportService;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredEvidenceBundleBo openEvidenceBundle(String jobId) {
        JobDiagnosticsReportVo diagnostics = queryService.getDiagnosticsReport(jobId);
        String markdown = evidenceReportService.buildMarkdownReport(jobId);
        byte[] diagnosticsJson = writeJson(diagnostics);
        byte[] manifestJson = writeJson(manifest(diagnostics));
        byte[] markdownBytes = markdown.getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes)) {
            writeEntry(zipOutputStream, "manifest.json", manifestJson);
            writeEntry(zipOutputStream, "evidence.md", markdownBytes);
            writeEntry(zipOutputStream, "diagnostics.json", diagnosticsJson);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create job evidence bundle.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new StoredEvidenceBundleBo(
                "linguaframe-job-%s-evidence.zip".formatted(jobId),
                CONTENT_TYPE,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private Map<String, Object> manifest(JobDiagnosticsReportVo diagnostics) {
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
        manifest.put("artifactCount", diagnostics.artifactCount());
        manifest.put("entries", List.of("manifest.json", "evidence.md", "diagnostics.json"));
        manifest.put("safety", Map.of(
                        "includesMediaBytes", false,
                        "includesRawTranscriptText", false,
                        "includesRawSubtitleText", false,
                        "includesObjectKeys", false,
                        "includesProviderPayloads", false
                ));
        return manifest;
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write job evidence bundle JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }
}
