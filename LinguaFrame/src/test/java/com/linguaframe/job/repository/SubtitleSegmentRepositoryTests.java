package com.linguaframe.job.repository;

import com.linguaframe.job.domain.entity.LocalizationJobRecord;
import com.linguaframe.job.domain.entity.SubtitleSegmentRecord;
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
class SubtitleSegmentRepositoryTests {

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private LocalizationJobRepository jobRepository;

    @Autowired
    private SubtitleSegmentRepository subtitleSegmentRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanDatabase() {
        jdbcClient.sql("DELETE FROM subtitle_segments").update();
        jdbcClient.sql("DELETE FROM transcript_segments").update();
        jdbcClient.sql("DELETE FROM job_artifacts").update();
        jdbcClient.sql("DELETE FROM job_timeline_events").update();
        jdbcClient.sql("DELETE FROM job_dispatch_events").update();
        jdbcClient.sql("DELETE FROM localization_jobs").update();
        jdbcClient.sql("DELETE FROM videos").update();
    }

    @Test
    void findsSubtitleSegmentsByJobAndLanguageOrderedByIndexAndDeletesOnlyThatLanguage() {
        Instant createdAt = Instant.parse("2026-06-26T10:00:00Z");
        createJob("subtitle-video-1", "subtitle-job-1", createdAt);
        createJob("subtitle-video-2", "subtitle-job-2", createdAt);
        SubtitleSegmentRecord later = new SubtitleSegmentRecord(
                "subtitle-segment-2",
                "subtitle-job-1",
                "zh-CN",
                1,
                1_800L,
                3_600L,
                "这个演示字幕是确定性的。",
                createdAt.plusSeconds(2)
        );
        SubtitleSegmentRecord earlier = new SubtitleSegmentRecord(
                "subtitle-segment-1",
                "subtitle-job-1",
                "zh-CN",
                0,
                0L,
                1_800L,
                "LinguaFrame 向你问好。",
                createdAt.plusSeconds(1)
        );
        SubtitleSegmentRecord otherLanguage = new SubtitleSegmentRecord(
                "subtitle-segment-en",
                "subtitle-job-1",
                "en-US",
                0,
                0L,
                1_800L,
                "Hello from LinguaFrame.",
                createdAt.plusSeconds(3)
        );
        SubtitleSegmentRecord otherJob = new SubtitleSegmentRecord(
                "subtitle-segment-other-job",
                "subtitle-job-2",
                "zh-CN",
                0,
                0L,
                900L,
                "另一个任务。",
                createdAt.plusSeconds(4)
        );

        subtitleSegmentRepository.saveAll(List.of(later, earlier, otherLanguage, otherJob));

        assertThat(subtitleSegmentRepository.findByJobIdAndLanguage("subtitle-job-1", "zh-CN"))
                .containsExactly(earlier, later);

        subtitleSegmentRepository.deleteByJobIdAndLanguage("subtitle-job-1", "zh-CN");

        assertThat(subtitleSegmentRepository.findByJobIdAndLanguage("subtitle-job-1", "zh-CN")).isEmpty();
        assertThat(subtitleSegmentRepository.findByJobIdAndLanguage("subtitle-job-1", "en-US"))
                .containsExactly(otherLanguage);
        assertThat(subtitleSegmentRepository.findByJobIdAndLanguage("subtitle-job-2", "zh-CN"))
                .containsExactly(otherJob);
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
