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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

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
    }
}
