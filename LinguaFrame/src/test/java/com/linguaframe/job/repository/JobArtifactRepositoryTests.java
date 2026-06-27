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
        assertThat(artifactRepository.findById("missing-artifact")).isEmpty();
        assertThat(artifactRepository.findByJobId("artifact-job-1"))
                .containsExactly(second, first);
    }

    private void createJob(String videoId, String jobId, Instant createdAt) {
        videoRepository.save(new VideoRecord(
                videoId,
                "sample.mp4",
                "video/mp4",
                123L,
                "source-videos/" + videoId + "/sample.mp4",
                MediaUploadStatus.UPLOADED,
                createdAt
        ));
        jobRepository.save(new LocalizationJobRecord(
                jobId,
                videoId,
                "zh-CN",
                LocalizationJobStatus.QUEUED,
                createdAt
        ));
    }
}
