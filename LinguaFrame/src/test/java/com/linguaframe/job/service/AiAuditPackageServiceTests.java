package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStage;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.ModelCallOperation;
import com.linguaframe.job.domain.enums.ModelCallProvider;
import com.linguaframe.job.domain.enums.ModelCallStatus;
import com.linguaframe.job.domain.enums.PromptTemplatePurpose;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.ModelCallVo;
import com.linguaframe.job.domain.vo.PromptTemplateVo;
import com.linguaframe.job.service.impl.AiAuditPackageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class AiAuditPackageServiceTests {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void opensMetadataOnlyAiAuditPackage() throws IOException {
        AiAuditPackageService service = new AiAuditPackageServiceImpl(
                new RecordingLocalizationJobQueryService(),
                new RecordingPromptTemplateRegistry(),
                objectMapper
        );

        var result = service.openAiAuditPackage("job-ai-audit");

        assertThat(result.filename()).isEqualTo("linguaframe-job-job-ai-audit-ai-audit-package.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.sizeBytes()).isPositive();
        Map<String, String> entries = readZipEntries(result.inputStream());
        assertThat(entries)
                .containsOnlyKeys(
                        "manifest.json",
                        "README.md",
                        "model-calls.json",
                        "prompt-templates.json",
                        "ai-usage-summary.json",
                        "ai-audit-report.md"
                );

        assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-ai-audit\"")
                .contains("\"modelCallCount\":3")
                .contains("\"promptTemplateCount\":2")
                .contains("\"includesProviderPayloads\":false");
        assertThat(entries.get("model-calls.json"))
                .contains("openai-audio-transcriptions-v1")
                .contains("openai-subtitle-translation-v1")
                .contains("openai-translation-quality-evaluation-v1")
                .contains("omitted because it contained fields outside the AI audit safety contract");
        assertThat(entries.get("prompt-templates.json"))
                .contains("SUBTITLE_TRANSLATION")
                .contains("TRANSLATION_QUALITY_EVALUATION");
        assertThat(entries.get("ai-usage-summary.json"))
                .contains("modelCallCount")
                .contains("\"failedModelCallCount\":1")
                .contains("\"OPENAI\":3");
        assertThat(entries.get("ai-audit-report.md"))
                .contains("# LinguaFrame AI Audit Report")
                .contains("- Job: job-ai-audit")
                .contains("- Model calls: 3")
                .contains("- Failed model calls: 1");

        String combined = String.join("\n", entries.values());
        assertThat(combined)
                .doesNotContain("/Users/")
                .doesNotContain("job-artifacts/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("sk-test")
                .doesNotContain("provider request payload")
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text");
    }

    private static Map<String, String> readZipEntries(java.io.InputStream inputStream) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private record RecordingLocalizationJobQueryService() implements LocalizationJobQueryService {
        @Override
        public LocalizationJobListVo listJobs(LocalizationJobStatus status, Integer limit, Integer offset) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocalizationJobVo getJob(String jobId) {
            return new LocalizationJobVo(
                    "job-ai-audit",
                    "video-ai-audit",
                    "zh-CN",
                    "verse",
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T12:00:00Z"),
                    Instant.parse("2026-06-28T12:00:01Z"),
                    Instant.parse("2026-06-28T12:01:00Z"),
                    null,
                    null,
                    "raw transcript text raw subtitle text provider request payload sk-test /Users/example job-artifacts/raw.json OPENAI_API_KEY",
                    0,
                    JobDispatchEventStatus.DISPATCHED,
                    1,
                    Instant.parse("2026-06-28T12:00:01Z"),
                    List.of(),
                    new JobUsageSummaryVo(3, 1, 1800, new BigDecimal("0.04500000"), 1200, 600, new BigDecimal("32.5"), 640),
                    new JobCacheSummaryVo(1, 4, 1),
                    List.of(
                            modelCall(
                                    "model-call-transcription",
                                    LocalizationJobStage.TRANSCRIPT_SUBTITLE_EXPORT,
                                    ModelCallOperation.TRANSCRIPTION,
                                    "gpt-4o-mini-transcribe",
                                    "openai-audio-transcriptions-v1",
                                    ModelCallStatus.SUCCEEDED,
                                    "audioSeconds=32.5",
                                    "segmentCount=8"
                            ),
                            modelCall(
                                    "model-call-translation",
                                    LocalizationJobStage.TARGET_SUBTITLE_EXPORT,
                                    ModelCallOperation.TRANSLATION,
                                    "gpt-4.1-mini",
                                    "openai-subtitle-translation-v1",
                                    ModelCallStatus.SUCCEEDED,
                                    "segmentCount=8 characterCount=640",
                                    "translatedSegmentCount=8"
                            ),
                            modelCall(
                                    "model-call-evaluation",
                                    LocalizationJobStage.TRANSLATION_QUALITY_EVALUATION,
                                    ModelCallOperation.EVALUATION,
                                    "gpt-4.1-mini",
                                    "openai-translation-quality-evaluation-v1",
                                    ModelCallStatus.FAILED,
                                    "provider request payload raw transcript text",
                                    "sk-test /Users/example/job-artifacts/raw.json"
                            )
                    ),
                    null,
                    null,
                    null
            );
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            throw new UnsupportedOperationException();
        }

        private static ModelCallVo modelCall(
                String modelCallId,
                LocalizationJobStage stage,
                ModelCallOperation operation,
                String model,
                String promptVersion,
                ModelCallStatus status,
                String inputSummary,
                String outputSummary
        ) {
            return new ModelCallVo(
                    modelCallId,
                    "job-ai-audit",
                    stage,
                    operation,
                    ModelCallProvider.OPENAI,
                    model,
                    promptVersion,
                    status,
                    600L,
                    100,
                    50,
                    new BigDecimal("32.5"),
                    640,
                    inputSummary,
                    outputSummary,
                    "private-demo-owner",
                    new BigDecimal("0.01500000"),
                    status == ModelCallStatus.FAILED ? "provider request payload OPENAI_API_KEY" : null,
                    Instant.parse("2026-06-28T12:00:10Z")
            );
        }
    }

    private record RecordingPromptTemplateRegistry() implements PromptTemplateRegistry {
        @Override
        public PromptTemplateVo activeTemplate(PromptTemplatePurpose purpose) {
            return listActiveTemplates().stream()
                    .filter(template -> template.purpose() == purpose)
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public List<PromptTemplateVo> listActiveTemplates() {
            return List.of(
                    new PromptTemplateVo(
                            "openai-subtitle-translation-v1",
                            PromptTemplatePurpose.SUBTITLE_TRANSLATION,
                            "OPENAI",
                            "gpt-4.1",
                            "Translate subtitle segments for video localization.",
                            "Return JSON subtitle segments.",
                            true
                    ),
                    new PromptTemplateVo(
                            "openai-translation-quality-evaluation-v1",
                            PromptTemplatePurpose.TRANSLATION_QUALITY_EVALUATION,
                            "OPENAI",
                            "gpt-4.1",
                            "Evaluate translated subtitle quality.",
                            "Return score, verdict, dimensions, issues, and suggested fixes.",
                            true
                    )
            );
        }
    }
}
