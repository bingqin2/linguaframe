package com.linguaframe.job.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.linguaframe.job.domain.bo.StoredHandoffPackageBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.vo.JobCacheSummaryVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsArtifactVo;
import com.linguaframe.job.domain.vo.JobDiagnosticsReportVo;
import com.linguaframe.job.domain.vo.LocalizationJobListVo;
import com.linguaframe.job.domain.vo.LocalizationJobVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.service.impl.JobHandoffPackageServiceImpl;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JobHandoffPackageServiceTests {

    @Test
    void opensHandoffPackageWithReviewedArtifactsAndSafeEvidenceOnly() throws IOException {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        saveArtifact(artifactRepository, storageService, "reviewed-json", JobArtifactType.REVIEWED_SUBTITLE_JSON,
                "reviewed-subtitles.zh-CN.json", "application/json", "{\"segments\":[]}");
        saveArtifact(artifactRepository, storageService, "reviewed-srt", JobArtifactType.REVIEWED_SUBTITLE_SRT,
                "reviewed-subtitles.zh-CN.srt", "application/x-subrip",
                "1\n00:00:00,000 --> 00:00:01,000\nReviewed line\n");
        saveArtifact(artifactRepository, storageService, "reviewed-vtt", JobArtifactType.REVIEWED_SUBTITLE_VTT,
                "reviewed-subtitles.zh-CN.vtt", "text/vtt",
                "WEBVTT\n\n00:00.000 --> 00:01.000\nReviewed line\n");
        saveArtifact(artifactRepository, storageService, "reviewed-video", JobArtifactType.REVIEWED_BURNED_VIDEO,
                "reviewed-burned-video.mp4", "video/mp4", "reviewed-video-bytes");
        saveArtifact(artifactRepository, storageService, "target-json", JobArtifactType.TARGET_SUBTITLE_JSON,
                "target-subtitles.json", "application/json", "raw generated subtitle");
        saveArtifact(artifactRepository, storageService, "transcript-json", JobArtifactType.TRANSCRIPT_JSON,
                "transcript.json", "application/json", "raw transcript text");
        saveArtifact(artifactRepository, storageService, "burned-video", JobArtifactType.BURNED_VIDEO,
                "burned-video.mp4", "video/mp4", "provider payload");
        saveArtifact(artifactRepository, storageService, "summary", JobArtifactType.WORKER_SUMMARY,
                "worker-summary.json", "application/json", "OPENAI_API_KEY private-demo-token /Users/example");

        JobHandoffPackageService service = new JobHandoffPackageServiceImpl(
                artifactRepository,
                storageService,
                new RecordingLocalizationJobQueryService(),
                new RecordingDeliveryManifestService(),
                new RecordingJobEvidenceReportService(),
                JsonMapper.builder().findAndAddModules().build()
        );

        StoredHandoffPackageBo result = service.openHandoffPackage("job-handoff-package");

        assertThat(result.filename()).isEqualTo("linguaframe-job-job-handoff-package-handoff-package.zip");
        assertThat(result.contentType()).isEqualTo("application/zip");
        assertThat(result.sizeBytes()).isGreaterThan(0L);
        Map<String, String> entries = readZipEntries(result.inputStream());
        assertThat(entries)
                .containsKeys(
                        "manifest.json",
                        "delivery-manifest.md",
                        "evidence.md",
                        "diagnostics.json",
                        "reviewed/REVIEWED_SUBTITLE_JSON/reviewed-json-reviewed-subtitles.zh-CN.json",
                        "reviewed/REVIEWED_SUBTITLE_SRT/reviewed-srt-reviewed-subtitles.zh-CN.srt",
                        "reviewed/REVIEWED_SUBTITLE_VTT/reviewed-vtt-reviewed-subtitles.zh-CN.vtt",
                        "reviewed/REVIEWED_BURNED_VIDEO/reviewed-video-reviewed-burned-video.mp4"
                )
                .doesNotContainKeys(
                        "reviewed/TARGET_SUBTITLE_JSON/target-json-target-subtitles.json",
                        "reviewed/TRANSCRIPT_JSON/transcript-json-transcript.json",
                        "reviewed/BURNED_VIDEO/burned-video-burned-video.mp4"
                );
        assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-handoff-package\"")
                .contains("\"handoffReady\":true")
                .contains("\"reviewedArtifactCount\":4")
                .contains("\"includesRawTranscriptText\":false")
                .contains("\"includesProviderPayloads\":false");
        String zipText = String.join("\n", entries.values());
        assertThat(zipText)
                .doesNotContain("raw transcript text")
                .doesNotContain("raw generated subtitle")
                .doesNotContain("job-artifacts/")
                .doesNotContain("/Users/")
                .doesNotContain("provider payload")
                .doesNotContain("OPENAI_API_KEY")
                .doesNotContain("private-demo-token");
    }

    private static void saveArtifact(
            InMemoryJobArtifactRepository repository,
            InMemoryObjectStorageService storageService,
            String id,
            JobArtifactType type,
            String filename,
            String contentType,
            String content
    ) {
        String objectKey = "job-artifacts/job-handoff-package/" + id + "/" + filename;
        storageService.objects.put(objectKey, content.getBytes(StandardCharsets.UTF_8));
        repository.records.add(new JobArtifactRecord(
                id,
                "job-handoff-package",
                type,
                objectKey,
                filename,
                contentType,
                content.getBytes(StandardCharsets.UTF_8).length,
                "hash-" + id,
                false,
                null,
                Instant.parse("2026-06-28T12:00:00Z")
        ));
    }

    private static Map<String, String> readZipEntries(InputStream inputStream) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream)) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static final class InMemoryJobArtifactRepository extends JobArtifactRepository {
        private final List<JobArtifactRecord> records = new java.util.ArrayList<>();

        private InMemoryJobArtifactRepository() {
            super(null);
        }

        @Override
        public List<JobArtifactRecord> findByJobId(String jobId) {
            return records.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }
    }

    private static final class InMemoryObjectStorageService implements ObjectStorageService {
        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream open(String objectKey) {
            return new ByteArrayInputStream(objects.get(objectKey));
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
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
                    Instant.parse("2026-06-28T12:00:01Z"),
                    job(),
                    List.of(new JobDiagnosticsArtifactVo(
                            "reviewed-srt",
                            JobArtifactType.REVIEWED_SUBTITLE_SRT,
                            "reviewed-subtitles.zh-CN.srt",
                            "application/x-subrip",
                            48L,
                            "hash-reviewed-srt",
                            false,
                            null,
                            Instant.parse("2026-06-28T12:00:00Z")
                    )),
                    1
            );
        }

        private static LocalizationJobVo job() {
            return new LocalizationJobVo(
                    "job-handoff-package",
                    "video-handoff-package",
                    "zh-CN",
                    "verse",
                    LocalizationJobStatus.COMPLETED,
                    Instant.parse("2026-06-28T12:00:00Z"),
                    Instant.parse("2026-06-28T12:00:01Z"),
                    Instant.parse("2026-06-28T12:00:20Z"),
                    null,
                    null,
                    null,
                    0,
                    null,
                    0,
                    null,
                    List.of(),
                    null,
                    new JobCacheSummaryVo(0, 0, 0),
                    List.of(),
                    null,
                    null,
                    null
            );
        }
    }

    private record RecordingDeliveryManifestService() implements DeliveryManifestService {
        @Override
        public com.linguaframe.job.domain.vo.DeliveryManifestVo buildManifest(String jobId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String buildMarkdownManifest(String jobId) {
            return """
                    # LinguaFrame Delivery Manifest

                    - Job: job-handoff-package
                    - Handoff ready: true
                    """;
        }
    }

    private record RecordingJobEvidenceReportService() implements JobEvidenceReportService {
        @Override
        public String buildMarkdownReport(String jobId) {
            return """
                    # LinguaFrame Demo Evidence

                    - Job: job-handoff-package
                    - Status: COMPLETED
                    """;
        }
    }
}
