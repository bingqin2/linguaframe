package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.TranscriptSegmentRecord;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TranscriptSegmentRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void savesAndFindsSegmentsOrderedByIndex() {
        Instant createdAt = Instant.parse("2026-06-26T10:00:00Z");
        createJob("transcript-video-1", "transcript-job-1", createdAt);
        createJob("transcript-video-2", "transcript-job-2", createdAt);
        TranscriptSegmentRecord later = new TranscriptSegmentRecord(
                "transcript-segment-2",
                "transcript-job-1",
                1,
                1_200L,
                2_500L,
                "Second segment",
                createdAt.plusSeconds(2)
        );
        TranscriptSegmentRecord earlier = new TranscriptSegmentRecord(
                "transcript-segment-1",
                "transcript-job-1",
                0,
                0L,
                1_200L,
                "First segment",
                createdAt.plusSeconds(1)
        );
        TranscriptSegmentRecord otherJob = new TranscriptSegmentRecord(
                "transcript-segment-other",
                "transcript-job-2",
                0,
                0L,
                900L,
                "Other job segment",
                createdAt.plusSeconds(3)
        );

        transcriptSegmentRepository.saveAll(List.of(later, earlier, otherJob));

        assertThat(transcriptSegmentRepository.findByJobId("transcript-job-1"))
                .containsExactly(earlier, later);
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
