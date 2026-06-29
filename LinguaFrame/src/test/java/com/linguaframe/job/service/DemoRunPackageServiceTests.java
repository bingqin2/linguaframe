package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linguaframe.job.domain.bo.StoredQualityEvidenceBo;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.JobDispatchEventStatus;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.enums.QualityEvaluationStatus;
import com.linguaframe.job.domain.vo.DeliveryManifestLinkVo;
import com.linguaframe.job.domain.vo.DeliveryManifestVo;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.JobUsageSummaryVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.domain.vo.QualityEvaluationVo;
import com.linguaframe.job.service.impl.DemoRunPackageServiceImpl;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class DemoRunPackageServiceTests {

    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();

    @Test
    void opensMetadataOnlyDemoRunPackage() throws IOException {
        DemoRunPackageService service = new DemoRunPackageServiceImpl(
                new RecordingLocalizationJobQueryService(),
                new RecordingJobEvidenceReportService(),
                new RecordingQualityEvaluationEvidenceService(),
                new RecordingDeliveryManifestService(),
                objectMapper
        );

        var result = service.openDemoRunPackage("job-demo-package");

        assertThat(result.filename()).isEqualTo("linguaframe-job-job-demo-package-demo-run-package.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.sizeBytes()).isPositive();
        Map<String, String> entries = readZipEntries(result.inputStream());
        assertThat(entries)
                .containsKeys(
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
        assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-demo-package\"")
                .contains("\"includesMediaBytes\":false")
                .contains("\"includesRawTranscriptText\":false")
                .contains("\"includesProviderPayloads\":false");
        assertThat(entries.get("README.md"))
                .contains("# LinguaFrame Demo Run Package")
                .contains("- Job: job-demo-package")
                .contains("- Demo run package: /api/jobs/job-demo-package/demo-run-package/download");
        assertThat(entries.get("demo-handoff-checklist.md"))
                .contains("# LinguaFrame Demo Handoff Checklist")
                .contains("- PASS Job completed")
                .contains("- PASS Media outputs available")
                .contains("- PASS Quality evaluation available");
        assertThat(entries.get("demo-session-report.md"))
                .contains("# LinguaFrame Demo Session Report")
                .contains("- Overall: READY")
                .contains("- Media outputs: 3")
                .contains("- Quality: 92 / 100, GOOD, SUCCEEDED");

        String combined = String.join("\n", entries.values());
        assertThat(combined)
                .doesNotContain("raw transcript text")
                .doesNotContain("raw subtitle text")
                .doesNotContain("provider request payload")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token")
                .doesNotContain("sk-");
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
            return job();
        }

        @Override
        public JobDiagnosticsReportVo getDiagnosticsReport(String jobId) {
            return new JobDiagnosticsReportVo(
                    Instant.parse("2026-06-28T12:01:00Z"),
                    job(),
                    List.of(
                            new JobDiagnosticsArtifactVo(
                                    "reviewed-srt",
                                    JobArtifactType.REVIEWED_SUBTITLE_SRT,
                                    "reviewed-subtitles.zh-CN.srt",
                                    "application/x-subrip",
                                    120L,
                                    "hash-reviewed-srt",
                                    false,
                                    null,
                                    Instant.parse("2026-06-28T12:00:30Z")
                            ),
                            new JobDiagnosticsArtifactVo(
                                    "reviewed-json",
                                    JobArtifactType.REVIEWED_SUBTITLE_JSON,
                                    "reviewed-subtitles.zh-CN.json",
                                    "application/json",
                                    120L,
                                    "hash-reviewed-json",
                                    false,
                                    null,
                                    Instant.parse("2026-06-28T12:00:30Z")
                            ),
                            new JobDiagnosticsArtifactVo(
                                    "reviewed-vtt",
                                    JobArtifactType.REVIEWED_SUBTITLE_VTT,
                                    "reviewed-subtitles.zh-CN.vtt",
                                    "text/vtt",
                                    120L,
                                    "hash-reviewed-vtt",
                                    false,
                                    null,
                                    Instant.parse("2026-06-28T12:00:30Z")
                            ),
                            new JobDiagnosticsArtifactVo(
                                    "burned-video",
                                    JobArtifactType.BURNED_VIDEO,
                                    "burned-video.mp4",
                                    "video/mp4",
                                    4096L,
                                    "hash-burned-video",
                                    false,
                                    null,
                                    Instant.parse("2026-06-28T12:00:31Z")
                            ),
                            new JobDiagnosticsArtifactVo(
                                    "dubbed-video",
                                    JobArtifactType.DUBBED_VIDEO,
                                    "dubbed-video.mp4",
                                    "video/mp4",
                                    8192L,
                                    "hash-dubbed-video",
                                    false,
                                    null,
                                    Instant.parse("2026-06-28T12:00:32Z")
                            ),
                            new JobDiagnosticsArtifactVo(
                                    "narrated-video",
                                    JobArtifactType.NARRATED_VIDEO,
                                    "narrated-video.mp4",
                                    "video/mp4",
                                    16384L,
                                    "hash-narrated-video",
                                    false,
                                    null,
                                    Instant.parse("2026-06-28T12:00:33Z")
                            )
                    ),
                    6
            );
        }

        private static LocalizationJobVo job() {
            return new LocalizationJobVo(
                    "job-demo-package",
                    "video-demo-package",
                    "zh-CN",
                    "verse",
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T12:00:00Z"),
                    Instant.parse("2026-06-28T12:00:01Z"),
                    Instant.parse("2026-06-28T12:00:30Z"),
                    null,
                    null,
                    "provider request payload raw transcript text raw subtitle text sk-test /Users/example job-artifacts/raw.json OPENAI_API_KEY private-demo-token",
                    0,
                    JobDispatchEventStatus.DISPATCHED,
                    1,
                    Instant.parse("2026-06-28T12:00:01Z"),
                    List.of(),
                    new JobUsageSummaryVo(2, 0, 1200, BigDecimal.valueOf(0.012), 1000, 500, null, null),
                    new JobCacheSummaryVo(1, 3, 1),
                    List.of(),
                    new QualityEvaluationVo(
                            "quality-demo-package",
                            "job-demo-package",
                            "zh-CN",
                            92,
                            "GOOD",
                            95,
                            92,
                            94,
                            88,
                            List.of("No blocking issue."),
                            List.of("Review terminology."),
                            QualityEvaluationStatus.SUCCEEDED,
                            null,
                            Instant.parse("2026-06-28T12:00:20Z")
                    ),
                    null,
                    null
            );
        }
    }

    private record RecordingJobEvidenceReportService() implements JobEvidenceReportService {
        @Override
        public String buildMarkdownReport(String jobId) {
            return """
                    # LinguaFrame Demo Evidence

                    - Job: job-demo-package
                    - Status: COMPLETED
                    """;
        }
    }

    private record RecordingQualityEvaluationEvidenceService() implements QualityEvaluationEvidenceService {
        @Override
        public StoredQualityEvidenceBo openMarkdownEvidence(String jobId) {
            byte[] body = """
                    # LinguaFrame Quality Evaluation Evidence

                    - Job: job-demo-package
                    - Score: 92 / 100
                    """.getBytes(StandardCharsets.UTF_8);
            return new StoredQualityEvidenceBo(
                    "linguaframe-job-job-demo-package-quality-evidence.md",
                    "text/markdown;charset=UTF-8",
                    body.length,
                    new ByteArrayInputStream(body)
            );
        }
    }

    private record RecordingDeliveryManifestService() implements DeliveryManifestService {
        @Override
        public DeliveryManifestVo buildManifest(String jobId) {
            return new DeliveryManifestVo(
                    jobId,
                    "video-demo-package",
                    "zh-CN",
                    "STANDARD",
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T12:01:00Z"),
                    true,
                    1,
                    false,
                    2,
                    List.of(),
                    List.of(),
                    List.of(new DeliveryManifestLinkVo(
                            "Demo run package",
                            "DEMO_RUN_PACKAGE",
                            "/api/jobs/job-demo-package/demo-run-package/download"
                    ))
            );
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            return """
                    # LinguaFrame Delivery Manifest

                    - Job: job-demo-package
                    - Handoff ready: true
                    """;
        }
    }
}
