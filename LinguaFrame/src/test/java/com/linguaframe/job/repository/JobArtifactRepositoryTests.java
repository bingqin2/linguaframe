package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.JobArtifactRecord;
import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.enums.JobArtifactType;
import com.linguaframe.job.domain.enums.LocalizationJobStatus;
import com.linguaframe.media.domain.entity.VideoRecord;
import com.linguaframe.media.domain.enums.MediaUploadStatus;
import com.linguaframe.media.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class JobArtifactRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private JobArtifactRepository artifactRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void savesFindsAndListsArtifactsByJob() {
        Instant createdAt = Instant.parse("2026-06-26T10:00:00Z");
        createJob("artifact-video-1", "artifact-job-1", createdAt);
        createJob("artifact-video-2", "artifact-job-2", createdAt);
        JobArtifactRecord first = new JobArtifactRecord(
                "artifact-1",
                "artifact-job-1",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/artifact-job-1/artifact-1/worker-summary.json",
                "worker-summary.json",
                "application/json",
                123L,
                "abc123",
                false,
                null,
                createdAt.plusSeconds(2)
        );
        JobArtifactRecord second = new JobArtifactRecord(
                "artifact-2",
                "artifact-job-1",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/artifact-job-1/artifact-2/worker-summary.json",
                "worker-summary.json",
                "application/json",
                456L,
                "def456",
                true,
                "artifact-source",
                createdAt.plusSeconds(1)
        );
        JobArtifactRecord otherJob = new JobArtifactRecord(
                "artifact-other-job",
                "artifact-job-2",
                JobArtifactType.WORKER_SUMMARY,
                "job-artifacts/artifact-job-2/artifact-other-job/worker-summary.json",
                "worker-summary.json",
                "application/json",
                789L,
                "789abc",
                false,
                null,
                createdAt.plusSeconds(3)
        );

        artifactRepository.save(first);
        artifactRepository.save(second);
        artifactRepository.save(otherJob);

        assertThat(artifactRepository.findById("artifact-1")).contains(first);
        assertThat(artifactRepository.findById("artifact-1"))
                .get()
                .extracting(JobArtifactRecord::contentSha256)
                .isEqualTo("abc123");
        assertThat(artifactRepository.findById("artifact-2"))
                .get()
                .satisfies(record -> {
                    assertThat(record.cacheHit()).isTrue();
                    assertThat(record.sourceArtifactId()).isEqualTo("artifact-source");
                });
        assertThat(artifactRepository.findById("missing-artifact")).isEmpty();
        assertThat(artifactRepository.findByJobId("artifact-job-1"))
                .containsExactly(second, first);
    }

    @Test
    void findsNewestReusableGeneratedArtifactForVideoLanguageAndType() {
        Instant createdAt = Instant.parse("2026-06-27T08:00:00Z");
        createVideo("reuse-video", createdAt);
        createJobForExistingVideo("reuse-video", "reuse-source-job-old", createdAt);
        createJobForExistingVideo("reuse-video", "reuse-source-job-new", createdAt.plusSeconds(1));
        createJobForExistingVideo("reuse-video", "reuse-current-job", createdAt.plusSeconds(2));
        createJob("different-video", "reuse-different-video-job", createdAt.plusSeconds(3));
        JobArtifactRecord oldReusable = artifact(
                "reuse-artifact-old",
                "reuse-source-job-old",
                JobArtifactType.BURNED_VIDEO,
                "old-hash",
                false,
                null,
                createdAt.plusSeconds(4)
        );
        JobArtifactRecord newestReusable = artifact(
                "reuse-artifact-new",
                "reuse-source-job-new",
                JobArtifactType.BURNED_VIDEO,
                "new-hash",
                false,
                null,
                createdAt.plusSeconds(5)
        );
        JobArtifactRecord cacheHitArtifact = artifact(
                "reuse-artifact-cache-hit",
                "reuse-source-job-new",
                JobArtifactType.BURNED_VIDEO,
                "cache-hit-hash",
                true,
                "reuse-artifact-new",
                createdAt.plusSeconds(6)
        );
        JobArtifactRecord wrongType = artifact(
                "reuse-artifact-wrong-type",
                "reuse-source-job-new",
                JobArtifactType.WORKER_SUMMARY,
                "wrong-type-hash",
                false,
                null,
                createdAt.plusSeconds(7)
        );
        JobArtifactRecord wrongVideo = artifact(
                "reuse-artifact-wrong-video",
                "reuse-different-video-job",
                JobArtifactType.BURNED_VIDEO,
                "wrong-video-hash",
                false,
                null,
                createdAt.plusSeconds(8)
        );

        artifactRepository.save(oldReusable);
        artifactRepository.save(newestReusable);
        artifactRepository.save(cacheHitArtifact);
        artifactRepository.save(wrongType);
        artifactRepository.save(wrongVideo);

        assertThat(artifactRepository.findReusableArtifact("reuse-video", "zh-CN", JobArtifactType.BURNED_VIDEO))
                .contains(newestReusable);
        assertThat(artifactRepository.findReusableArtifact("reuse-video", "en-US", JobArtifactType.BURNED_VIDEO))
                .isEmpty();
    }

    private JobArtifactRecord artifact(
            String artifactId,
            String jobId,
            JobArtifactType type,
            String contentSha256,
            boolean cacheHit,
            String sourceArtifactId,
            Instant createdAt
    ) {
        return new JobArtifactRecord(
                artifactId,
                jobId,
                type,
                "job-artifacts/" + jobId + "/" + artifactId + "/" + artifactId + ".bin",
                artifactId + ".bin",
                "application/octet-stream",
                10L,
                contentSha256,
                cacheHit,
                sourceArtifactId,
                createdAt
        );
    }

    private void createJob(String videoId, String jobId, Instant createdAt) {
        createVideo(videoId, createdAt);
        createJobForExistingVideo(videoId, jobId, createdAt);
    }

    private void createVideo(String videoId, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/" + videoId + "/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
    }

    private void createJobForExistingVideo(String videoId, String jobId, Instant createdAt) {
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
    }
}
