package com.linguaframe.job.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linguaframe.job.domain.bo.StoredAiAuditPackageBo;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.service.AiAuditPackageService;
import com.linguaframe.job.service.LocalizationJobQueryService;
import com.linguaframe.job.service.PromptTemplateRegistry;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class AiAuditPackageServiceImpl implements AiAuditPackageService {

    private static final String CONTENT_TYPE = "application/zip";
    private static final String OMITTED = "omitted because it contained fields outside the AI audit safety contract";
    private static final List<String> ENTRIES = List.of(
            "manifest.json",
            "README.md",
            "model-calls.json",
            "prompt-templates.json",
            "ai-usage-summary.json",
            "ai-audit-report.md"
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
    private final PromptTemplateRegistry promptTemplateRegistry;
    private final ObjectMapper objectMapper;

    public AiAuditPackageServiceImpl(
            LocalizationJobQueryService queryService,
            PromptTemplateRegistry promptTemplateRegistry,
            ObjectMapper objectMapper
    ) {
        this.queryService = queryService;
        this.promptTemplateRegistry = promptTemplateRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public StoredAiAuditPackageBo openAiAuditPackage(String jobId) {
        LocalizationJobVo job = queryService.getJob(jobId);
        List<PromptTemplateVo> promptTemplates = promptTemplateRegistry.listActiveTemplates();
        Map<String, Object> usageSummary = aiUsageSummary(job.modelCalls());

        ByteArrayOutputStream archiveBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(archiveBytes)) {
            writeEntry(zipOutputStream, "manifest.json", writeJson(manifest(job, promptTemplates)));
            writeEntry(zipOutputStream, "README.md", readmeMarkdown(job).getBytes(StandardCharsets.UTF_8));
            writeEntry(zipOutputStream, "model-calls.json", writeJson(safeModelCalls(job.modelCalls())));
            writeEntry(zipOutputStream, "prompt-templates.json", writeJson(safePromptTemplates(promptTemplates)));
            writeEntry(zipOutputStream, "ai-usage-summary.json", writeJson(usageSummary));
            writeEntry(zipOutputStream, "ai-audit-report.md",
                    auditReportMarkdown(job, promptTemplates, usageSummary).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create AI audit package.", ex);
        }

        byte[] content = archiveBytes.toByteArray();
        return new StoredAiAuditPackageBo(
                "linguaframe-job-%s-ai-audit-package.zip".formatted(safeFilenamePart(job.jobId())),
                CONTENT_TYPE,
                content.length,
                new ByteArrayInputStream(content)
        );
    }

    private Map<String, Object> manifest(LocalizationJobVo job, List<PromptTemplateVo> promptTemplates) {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("packageType", "AI_AUDIT");
        manifest.put("generatedAt", Instant.now());
        manifest.put("jobId", job.jobId());
        manifest.put("videoId", job.videoId());
        manifest.put("targetLanguage", job.targetLanguage());
        manifest.put("demoProfileId", job.demoProfileId());
        manifest.put("subtitleStylePreset", job.subtitleStylePreset());
        manifest.put("translationGlossaryEntryCount", job.translationGlossaryEntryCount());
        manifest.put("translationGlossaryHash", job.translationGlossaryHash());
        manifest.put("subtitlePolishingMode", job.subtitlePolishingMode());
        manifest.put("status", job.status());
        manifest.put("entries", ENTRIES);
        manifest.put("modelCallCount", job.modelCalls().size());
        manifest.put("promptTemplateCount", promptTemplates.size());
        manifest.put("includesRawTranscriptText", false);
        manifest.put("includesRawSubtitleText", false);
        manifest.put("includesCorrectedSubtitleText", false);
        manifest.put("includesProviderPayloads", false);
        manifest.put("includesObjectKeys", false);
        manifest.put("includesLocalPaths", false);
        manifest.put("includesCredentials", false);
        manifest.put("includesMediaBytes", false);
        return manifest;
    }

    private List<Map<String, Object>> safeModelCalls(List<ModelCallVo> modelCalls) {
        return modelCalls.stream()
                .map(call -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("modelCallId", call.modelCallId());
                    row.put("jobId", call.jobId());
                    row.put("stage", call.stage());
                    row.put("operation", call.operation());
                    row.put("provider", call.provider());
                    row.put("model", call.model());
                    row.put("promptVersion", call.promptVersion());
                    row.put("status", call.status());
                    row.put("latencyMs", call.latencyMs());
                    row.put("inputTokens", call.inputTokens());
                    row.put("outputTokens", call.outputTokens());
                    row.put("audioSeconds", call.audioSeconds());
                    row.put("characterCount", call.characterCount());
                    row.put("inputSummary", safeText(call.inputSummary()));
                    row.put("outputSummary", safeText(call.outputSummary()));
                    row.put("budgetIdentity", safeText(call.budgetIdentity()));
                    row.put("estimatedCostUsd", call.estimatedCostUsd());
                    row.put("safeErrorSummary", safeText(call.safeErrorSummary()));
                    row.put("createdAt", call.createdAt());
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> safePromptTemplates(List<PromptTemplateVo> promptTemplates) {
        return promptTemplates.stream()
                .map(template -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("version", template.version());
                    row.put("purpose", template.purpose());
                    row.put("provider", template.provider());
                    row.put("modelFamily", template.modelFamily());
                    row.put("systemPrompt", safeText(template.systemPrompt()));
                    row.put("outputContract", safeText(template.outputContract()));
                    row.put("active", template.active());
                    return row;
                })
                .toList();
    }

    private Map<String, Object> aiUsageSummary(List<ModelCallVo> modelCalls) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("modelCallCount", modelCalls.size());
        summary.put("failedModelCallCount", modelCalls.stream()
                .filter(call -> call.status() == ModelCallStatus.FAILED)
                .count());
        summary.put("totalLatencyMs", modelCalls.stream()
                .mapToLong(ModelCallVo::latencyMs)
                .sum());
        summary.put("estimatedCostUsd", modelCalls.stream()
                .map(ModelCallVo::estimatedCostUsd)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("inputTokens", sumInteger(modelCalls.stream().map(ModelCallVo::inputTokens).toList()));
        summary.put("outputTokens", sumInteger(modelCalls.stream().map(ModelCallVo::outputTokens).toList()));
        summary.put("audioSeconds", modelCalls.stream()
                .map(ModelCallVo::audioSeconds)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.put("characterCount", sumInteger(modelCalls.stream().map(ModelCallVo::characterCount).toList()));
        summary.put("byProvider", enumCounts(modelCalls, ModelCallProvider.class));
        summary.put("byOperation", enumCounts(modelCalls, ModelCallOperation.class));
        summary.put("byStatus", enumCounts(modelCalls, ModelCallStatus.class));
        return summary;
    }

    private Map<String, Long> enumCounts(List<ModelCallVo> modelCalls, Class<? extends Enum<?>> enumType) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Enum<?> constant : enumType.getEnumConstants()) {
            counts.put(constant.name(), 0L);
        }
        for (ModelCallVo call : modelCalls) {
            Enum<?> key = switch (enumType.getSimpleName()) {
                case "ModelCallProvider" -> call.provider();
                case "ModelCallOperation" -> call.operation();
                case "ModelCallStatus" -> call.status();
                default -> null;
            };
            if (key != null) {
                counts.put(key.name(), counts.getOrDefault(key.name(), 0L) + 1);
            }
        }
        return counts;
    }

    private Integer sumInteger(List<Integer> values) {
        int sum = 0;
        boolean hasValue = false;
        for (Integer value : values) {
            if (value != null) {
                hasValue = true;
                sum += value;
            }
        }
        return hasValue ? sum : null;
    }

    private String readmeMarkdown(LocalizationJobVo job) {
        return String.join("\n",
                "# LinguaFrame AI Audit Package",
                "",
                "- Job: " + job.jobId(),
                "- Video: " + job.videoId(),
                "- Target language: " + job.targetLanguage(),
                "- AI audit package: /api/jobs/" + job.jobId() + "/ai-audit-package/download",
                "",
                "## Entries",
                "",
                "- `manifest.json`: package metadata and safety flags.",
                "- `model-calls.json`: safe audited model-call rows.",
                "- `prompt-templates.json`: active prompt templates exposed by the backend.",
                "- `ai-usage-summary.json`: usage, latency, cost, provider, operation, and status counts.",
                "- `ai-audit-report.md`: readable reviewer summary.",
                "",
                "## Safety",
                "",
                "This package excludes raw media text, provider payloads, object keys, local paths, credentials, tokens, and media bytes.",
                ""
        );
    }

    private String auditReportMarkdown(
            LocalizationJobVo job,
            List<PromptTemplateVo> promptTemplates,
            Map<String, Object> usageSummary
    ) {
        Map<String, PromptTemplateVo> templatesByVersion = new LinkedHashMap<>();
        for (PromptTemplateVo template : promptTemplates) {
            templatesByVersion.put(template.version(), template);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("# LinguaFrame AI Audit Report\n\n");
        builder.append("- Job: ").append(job.jobId()).append("\n");
        builder.append("- Status: ").append(job.status()).append("\n");
        builder.append("- Model calls: ").append(usageSummary.get("modelCallCount")).append("\n");
        builder.append("- Failed model calls: ").append(usageSummary.get("failedModelCallCount")).append("\n");
        builder.append("- Estimated cost USD: ").append(usageSummary.get("estimatedCostUsd")).append("\n");
        builder.append("- Total latency ms: ").append(usageSummary.get("totalLatencyMs")).append("\n\n");
        builder.append("## Prompt Templates\n\n");
        for (PromptTemplateVo template : promptTemplates) {
            builder.append("- ").append(template.purpose())
                    .append(": ").append(template.version())
                    .append(" (").append(template.provider())
                    .append(", ").append(template.modelFamily()).append(")\n");
        }
        builder.append("\n## Model Calls\n\n");
        for (ModelCallVo call : job.modelCalls()) {
            PromptTemplateVo matchedTemplate = templatesByVersion.get(call.promptVersion());
            builder.append("- ").append(call.operation())
                    .append(" ").append(call.provider())
                    .append(" ").append(call.model())
                    .append(" ").append(call.status())
                    .append(" prompt=").append(call.promptVersion());
            if (matchedTemplate == null) {
                builder.append(" template=not-active");
            } else {
                builder.append(" template=").append(matchedTemplate.purpose());
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private String safeText(String value) {
        if (value == null) {
            return null;
        }
        return containsForbiddenMarker(value) ? OMITTED : value;
    }

    private boolean containsForbiddenMarker(String value) {
        return FORBIDDEN_MARKERS.stream().anyMatch(value::contains);
    }

    private byte[] writeJson(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write AI audit package JSON.", ex);
        }
    }

    private void writeEntry(ZipOutputStream zipOutputStream, String name, byte[] content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(name));
        zipOutputStream.write(content);
        zipOutputStream.closeEntry();
    }

    private String safeFilenamePart(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
