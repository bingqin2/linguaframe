package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.CreateJobArtifactCommand;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.service.impl.JobArtifactServiceImpl;
import com.linguaframe.storage.domain.bo.StoreObjectCommand;
import com.linguaframe.storage.domain.bo.StoredObjectBo;
import com.linguaframe.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobArtifactServiceTests {

    @Test
    void createsArtifactByStoringBytesAndSavingMetadata() {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        Instant now = Instant.parse("2026-06-26T10:00:00Z");
        JobArtifactService service = new JobArtifactServiceImpl(
                artifactRepository,
                storageService,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        JobArtifactVo result = service.createArtifact(new CreateJobArtifactCommand(
                "job-artifact-service-1",
                JobArtifactType.WORKER_SUMMARY,
                "worker-summary.json",
                "application/json",
                "{\"jobId\":\"job-artifact-service-1\"}".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(result.artifactId()).isNotBlank();
        assertThat(result.jobId()).isEqualTo("job-artifact-service-1");
        assertThat(result.type()).isEqualTo(JobArtifactType.WORKER_SUMMARY);
        assertThat(result.filename()).isEqualTo("worker-summary.json");
        assertThat(result.contentType()).isEqualTo("application/json");
        assertThat(result.sizeBytes()).isEqualTo(34);
        assertThat(result.contentSha256())
                .isEqualTo("a66f328b69820ccee2c4a3e7882e95e956e8e319b18020f43079398677dbfa33");
        assertThat(result.cacheHit()).isFalse();
        assertThat(result.sourceArtifactId()).isNull();
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(artifactRepository.saved).hasSize(1);
        assertThat(artifactRepository.saved.getFirst().contentSha256())
                .isEqualTo("a66f328b69820ccee2c4a3e7882e95e956e8e319b18020f43079398677dbfa33");
        assertThat(artifactRepository.saved.getFirst().objectKey())
                .isEqualTo("job-artifacts/job-artifact-service-1/" + result.artifactId() + "/worker-summary.json");
        assertThat(storageService.objects)
                .containsEntry(artifactRepository.saved.getFirst().objectKey(), "{\"jobId\":\"job-artifact-service-1\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void createsReusedArtifactWithoutStoringBytesAgain() {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        Instant now = Instant.parse("2026-06-26T10:30:00Z");
        JobArtifactService service = new JobArtifactServiceImpl(
                artifactRepository,
                storageService,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        com.linguaframe.job.domain.entity.JobArtifactRecord source =
                new com.linguaframe.job.domain.entity.JobArtifactRecord(
                        "source-artifact-1",
                        "source-job-1",
                        JobArtifactType.BURNED_VIDEO,
                        "job-artifacts/source-job-1/source-artifact-1/burned.mp4",
                        "burned.mp4",
                        "video/mp4",
                        100L,
                        "source-hash",
                        false,
                        null,
                        Instant.parse("2026-06-26T09:00:00Z")
                );

        JobArtifactVo result = service.createReusedArtifact("job-artifact-service-reuse", source);

        assertThat(result.jobId()).isEqualTo("job-artifact-service-reuse");
        assertThat(result.type()).isEqualTo(JobArtifactType.BURNED_VIDEO);
        assertThat(result.filename()).isEqualTo("burned.mp4");
        assertThat(result.contentType()).isEqualTo("video/mp4");
        assertThat(result.sizeBytes()).isEqualTo(100L);
        assertThat(result.contentSha256()).isEqualTo("source-hash");
        assertThat(result.cacheHit()).isTrue();
        assertThat(result.sourceArtifactId()).isEqualTo("source-artifact-1");
        assertThat(result.createdAt()).isEqualTo(now);
        assertThat(artifactRepository.saved)
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.jobId()).isEqualTo("job-artifact-service-reuse");
                    assertThat(record.objectKey()).isEqualTo(source.objectKey());
                    assertThat(record.cacheHit()).isTrue();
                    assertThat(record.sourceArtifactId()).isEqualTo("source-artifact-1");
                });
        assertThat(storageService.objects).isEmpty();
    }

    @Test
    void listsArtifactsForJob() {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        Instant now = Instant.parse("2026-06-26T11:00:00Z");
        JobArtifactService service = new JobArtifactServiceImpl(
                artifactRepository,
                storageService,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        JobArtifactVo artifact = service.createArtifact(new CreateJobArtifactCommand(
                "job-artifact-service-2",
                JobArtifactType.WORKER_SUMMARY,
                "worker-summary.json",
                "application/json",
                "{}".getBytes(StandardCharsets.UTF_8)
        ));

        assertThat(service.listArtifacts("job-artifact-service-2")).containsExactly(artifact);
        assertThat(service.listArtifacts("missing-job")).isEmpty();
    }

    @Test
    void opensArtifactOnlyWhenItBelongsToRequestedJob() throws IOException {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        Instant now = Instant.parse("2026-06-26T12:00:00Z");
        JobArtifactService service = new JobArtifactServiceImpl(
                artifactRepository,
                storageService,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        JobArtifactVo artifact = service.createArtifact(new CreateJobArtifactCommand(
                "job-artifact-service-3",
                JobArtifactType.WORKER_SUMMARY,
                "worker-summary.json",
                "application/json",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)
        ));

        var resource = service.openArtifact("job-artifact-service-3", artifact.artifactId());

        assertThat(resource.filename()).isEqualTo("worker-summary.json");
        assertThat(resource.contentType()).isEqualTo("application/json");
        assertThat(resource.sizeBytes()).isEqualTo(11);
        assertThat(new String(resource.inputStream().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("{\"ok\":true}");
        assertThatThrownBy(() -> service.openArtifact("other-job", artifact.artifactId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Job artifact not found.");
        assertThatThrownBy(() -> service.openArtifact("job-artifact-service-3", "missing-artifact"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Job artifact not found.");
    }

    @Test
    void opensArchiveWithManifestAndArtifactsForJob() throws IOException {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        JobArtifactService service = new JobArtifactServiceImpl(
                artifactRepository,
                storageService,
                Clock.fixed(Instant.parse("2026-06-27T09:00:00Z"), ZoneOffset.UTC)
        );
        JobArtifactVo summary = service.createArtifact(new CreateJobArtifactCommand(
                "job-artifact-archive",
                JobArtifactType.WORKER_SUMMARY,
                "worker-summary.json",
                "application/json",
                "{\"ok\":true}".getBytes(StandardCharsets.UTF_8)
        ));
        JobArtifactVo subtitles = service.createArtifact(new CreateJobArtifactCommand(
                "job-artifact-archive",
                JobArtifactType.TARGET_SUBTITLE_VTT,
                "target-subtitles.vtt",
                "text/vtt",
                "WEBVTT\n\n00:00.000 --> 00:01.000\n你好\n".getBytes(StandardCharsets.UTF_8)
        ));

        var archive = service.openArtifactArchive("job-artifact-archive");

        assertThat(archive.filename()).isEqualTo("linguaframe-job-job-artifact-archive-artifacts.zip");
        assertThat(archive.contentType()).isEqualTo("application/zip");
        assertThat(archive.sizeBytes()).isGreaterThan(0L);
        Map<String, String> entries = readZipEntries(archive.inputStream());
        assertThat(entries)
                .containsKeys(
                        "manifest.json",
                        "artifacts/WORKER_SUMMARY/" + summary.artifactId() + "-worker-summary.json",
                        "artifacts/TARGET_SUBTITLE_VTT/" + subtitles.artifactId() + "-target-subtitles.vtt"
                );
        assertThat(entries.get("manifest.json"))
                .contains("\"jobId\":\"job-artifact-archive\"")
                .contains("\"artifactCount\":2")
                .contains("\"type\":\"WORKER_SUMMARY\"")
                .contains("\"filename\":\"worker-summary.json\"")
                .contains("\"sizeBytes\":11")
                .contains("\"contentSha256\":\"")
                .contains("\"cacheHit\":false");
        assertThat(entries.get("artifacts/WORKER_SUMMARY/" + summary.artifactId() + "-worker-summary.json"))
                .isEqualTo("{\"ok\":true}");
    }

    @Test
    void sanitizesArchiveEntryFilenames() throws IOException {
        InMemoryJobArtifactRepository artifactRepository = new InMemoryJobArtifactRepository();
        InMemoryObjectStorageService storageService = new InMemoryObjectStorageService();
        JobArtifactService service = new JobArtifactServiceImpl(
                artifactRepository,
                storageService,
                Clock.fixed(Instant.parse("2026-06-27T09:30:00Z"), ZoneOffset.UTC)
        );
        JobArtifactVo dangerous = service.createArtifact(new CreateJobArtifactCommand(
                "job-artifact-archive-sanitize",
                JobArtifactType.BURNED_VIDEO,
                "../nested/path evil.mp4",
                "video/mp4",
                "video-bytes".getBytes(StandardCharsets.UTF_8)
        ));

        var archive = service.openArtifactArchive("job-artifact-archive-sanitize");

        assertThat(readZipEntries(archive.inputStream()))
                .containsKey("artifacts/BURNED_VIDEO/" + dangerous.artifactId() + "-path-evil.mp4")
                .doesNotContainKeys("../nested/path evil.mp4", "artifacts/BURNED_VIDEO/../nested/path evil.mp4");
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

    private static class InMemoryJobArtifactRepository extends JobArtifactRepository {

        private final List<com.linguaframe.job.domain.entity.JobArtifactRecord> saved = new java.util.ArrayList<>();

        private InMemoryJobArtifactRepository() {
            super(null);
        }

        @Override
        public void save(com.linguaframe.job.domain.entity.JobArtifactRecord record) {
            saved.add(record);
        }

        @Override
        public Optional<com.linguaframe.job.domain.entity.JobArtifactRecord> findById(String artifactId) {
            return saved.stream()
                    .filter(record -> record.id().equals(artifactId))
                    .findFirst();
        }

        @Override
        public List<com.linguaframe.job.domain.entity.JobArtifactRecord> findByJobId(String jobId) {
            return saved.stream()
                    .filter(record -> record.jobId().equals(jobId))
                    .toList();
        }

        @Override
        public Optional<com.linguaframe.job.domain.entity.JobArtifactRecord> findReusableArtifact(
                String videoId,
                String targetLanguage,
                JobArtifactType type,
                String subtitleStylePreset
        ) {
            return Optional.empty();
        }
    }

    private static class InMemoryObjectStorageService implements ObjectStorageService {

        private final Map<String, byte[]> objects = new HashMap<>();

        @Override
        public StoredObjectBo store(StoreObjectCommand command) {
            try {
                objects.put(command.objectKey(), command.inputStream().readAllBytes());
            } catch (IOException ex) {
                throw new IllegalStateException("Could not read input stream.", ex);
            }
            return new StoredObjectBo("linguaframe-artifacts", command.objectKey(), command.sizeBytes());
        }

        @Override
        public InputStream open(String objectKey) {
            byte[] content = objects.get(objectKey);
            if (content == null) {
                throw new IllegalStateException("Object not found.");
            }
            return new ByteArrayInputStream(content);
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }
    }
}
