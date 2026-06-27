package com.linguaframe.job.service;

import com.linguaframe.job.domain.bo.LocalizationJobExecutionContextBo;
import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.job.domain.message.QueuedLocalizationJobMessage;
import com.linguaframe.job.domain.vo.JobArtifactVo;
import com.linguaframe.job.repository.JobArtifactRepository;
import com.linguaframe.job.service.impl.ArtifactCacheServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ArtifactCacheServiceTests {

    @Test
    void returnsEmptyWhenNoReusableArtifactExists() {
        RecordingJobArtifactRepository artifactRepository = new RecordingJobArtifactRepository();
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        ArtifactCacheService service = new ArtifactCacheServiceImpl(artifactRepository, artifactService);

        Optional<JobArtifactVo> result = service.tryReuseArtifact(context(), JobArtifactType.BURNED_VIDEO);

        assertThat(result).isEmpty();
        assertThat(artifactRepository.lookupVideoId).isEqualTo("cache-video-1");
        assertThat(artifactRepository.lookupTargetLanguage).isEqualTo("zh-CN");
        assertThat(artifactRepository.lookupType).isEqualTo(JobArtifactType.BURNED_VIDEO);
        assertThat(artifactService.reusedSources).isEmpty();
    }

    @Test
    void createsReusedArtifactForCurrentJobWhenReusableArtifactExists() {
        Instant createdAt = Instant.parse("2026-06-27T09:00:00Z");
        JobArtifactRecord source = new JobArtifactRecord(
                "source-artifact-1",
                "source-job-1",
                JobArtifactType.BURNED_VIDEO,
                "job-artifacts/source-job-1/source-artifact-1/burned.mp4",
                "burned.mp4",
                "video/mp4",
                456L,
                "source-hash",
                false,
                null,
                createdAt
        );
        RecordingJobArtifactRepository artifactRepository = new RecordingJobArtifactRepository();
        artifactRepository.reusableArtifact = source;
        RecordingJobArtifactService artifactService = new RecordingJobArtifactService();
        ArtifactCacheService service = new ArtifactCacheServiceImpl(artifactRepository, artifactService);

        Optional<JobArtifactVo> result = service.tryReuseArtifact(context(), JobArtifactType.BURNED_VIDEO);

        assertThat(result)
                .get()
                .satisfies(artifact -> {
                    assertThat(artifact.jobId()).isEqualTo("cache-current-job");
                    assertThat(artifact.type()).isEqualTo(JobArtifactType.BURNED_VIDEO);
                    assertThat(artifact.contentSha256()).isEqualTo("source-hash");
                    assertThat(artifact.cacheHit()).isTrue();
                    assertThat(artifact.sourceArtifactId()).isEqualTo("source-artifact-1");
                });
        assertThat(artifactService.reusedSources).containsExactly(source);
    }

    private LocalizationJobExecutionContextBo context() {
        Instant createdAt = Instant.parse("2026-06-27T09:05:00Z");
        return new LocalizationJobExecutionContextBo(
                new LocalizationJobRecord(
                        "cache-current-job",
                        "cache-video-1",
                        "zh-CN",
                        LocalizationJobStatus.PROCESSING,
                        createdAt
                ),
                new QueuedLocalizationJobMessage(
                        "cache-current-job",
                        "cache-video-1",
                        "source-videos/cache-video-1/demo.mp4",
                        "zh-CN",
                        createdAt
                ),
                createdAt
        );
    }

    private static class RecordingJobArtifactRepository extends JobArtifactRepository {

        private JobArtifactRecord reusableArtifact;
        private String lookupVideoId;
        private String lookupTargetLanguage;
        private JobArtifactType lookupType;

        private RecordingJobArtifactRepository() {
            super(null);
        }

        @Override
        public Optional<JobArtifactRecord> findReusableArtifact(
                String videoId,
                String targetLanguage,
                JobArtifactType type
        ) {
            this.lookupVideoId = videoId;
            this.lookupTargetLanguage = targetLanguage;
            this.lookupType = type;
            return Optional.ofNullable(reusableArtifact);
        }
    }

    private static class RecordingJobArtifactService implements JobArtifactService {

        private final List<JobArtifactRecord> reusedSources = new ArrayList<>();

        @Override
        public JobArtifactVo createArtifact(com.linguaframe.job.domain.bo.CreateJobArtifactCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public JobArtifactVo createReusedArtifact(String jobId, JobArtifactRecord source) {
            reusedSources.add(source);
            return new JobArtifactVo(
                    "reused-" + source.id(),
                    jobId,
                    source.type(),
                    source.filename(),
                    source.contentType(),
                    source.sizeBytes(),
                    source.contentSha256(),
                    true,
                    source.id(),
                    Instant.parse("2026-06-27T09:10:00Z")
            );
        }

        @Override
        public List<JobArtifactVo> listArtifacts(String jobId) {
            return List.of();
        }

        @Override
        public com.linguaframe.job.domain.bo.StoredObjectResourceBo openArtifact(String jobId, String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}
